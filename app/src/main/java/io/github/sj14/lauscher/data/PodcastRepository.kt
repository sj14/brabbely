package io.github.sj14.lauscher.data

import android.util.Log
import androidx.room.withTransaction
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.data.db.AppDatabase
import io.github.sj14.lauscher.data.db.Chapter
import io.github.sj14.lauscher.data.db.Episode
import io.github.sj14.lauscher.data.db.Podcast
import io.github.sj14.lauscher.data.feed.ChaptersJson
import io.github.sj14.lauscher.data.feed.FeedParser
import io.github.sj14.lauscher.data.feed.ParsedEpisode
import io.github.sj14.lauscher.data.feed.ParsedFeed
import io.github.sj14.lauscher.data.opml.Opml
import io.github.sj14.lauscher.data.opml.OpmlEntry
import io.github.sj14.lauscher.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class PodcastRepository(
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val downloads: Downloads,
) {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()
    private val chapterDao = db.chapterDao()

    fun observeLibrary() = podcastDao.observeSubscribed(newSince())
    fun observePodcast(id: Long) = podcastDao.observe(id)
    fun observeEpisodes(podcastId: Long) = episodeDao.observeForPodcast(podcastId)
    fun observeEpisode(id: Long) = episodeDao.observeWithPodcast(id)
    fun observeNew() = episodeDao.observeNew(newSince())
    fun observeQueue() = episodeDao.observeQueue()
    fun observeDownloads() = episodeDao.observeDownloads()
    fun observeChapters(episodeId: Long) = chapterDao.observe(episodeId)

    /**
     * Loads a feed into the database without subscribing, so it can be previewed.
     * Returns the id of the podcast.
     */
    suspend fun preview(feedUrl: String): Long = withContext(Dispatchers.IO) {
        podcastDao.getByFeedUrl(feedUrl)?.let { return@withContext it.id }
        val feed = fetchFeed(feedUrl)
        db.withTransaction {
            val id = podcastDao.insert(feed.toPodcast(feedUrl).copy(lastRefreshed = System.currentTimeMillis()))
            insertEpisodes(id, feed.episodes)
            id
        }
    }

    suspend fun subscribe(podcastId: Long) = podcastDao.setSubscribed(podcastId, true)

    suspend fun unsubscribe(podcastId: Long) = withContext(Dispatchers.IO) {
        for (episode in episodeDao.getDownloadedForPodcast(podcastId)) {
            downloads.delete(episode)
        }
        podcastDao.delete(podcastId)
    }

    suspend fun deleteUnsubscribed() = podcastDao.deleteUnsubscribed()

    /** Refreshes one podcast and returns the episodes that are new since the last refresh. */
    suspend fun refresh(podcast: Podcast): List<Episode> = withContext(Dispatchers.IO) {
        val feed = fetchFeed(podcast.feedUrl)
        db.withTransaction {
            podcastDao.update(
                feed.toPodcast(podcast.feedUrl).copy(
                    id = podcast.id,
                    subscribed = podcast.subscribed,
                    lastRefreshed = System.currentTimeMillis(),
                ),
            )
            val known = episodeDao.getGuids(podcast.id).toSet()
            insertEpisodes(podcast.id, feed.episodes.filter { it.guid !in known })
        }
    }

    /** Refreshes all subscriptions and downloads new episodes if enabled. Returns the number of failed feeds. */
    suspend fun refreshAll(): Int {
        val current = settings.current()
        var failures = 0
        for (podcast in podcastDao.getSubscribed()) {
            try {
                val newEpisodes = refresh(podcast)
                if (current.autoDownload) {
                    newEpisodes.forEach { downloads.enqueue(it.id, current.wifiOnly) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Refreshing ${podcast.feedUrl} failed", e)
                failures++
            }
        }
        return failures
    }

    suspend fun loadChapters(episode: Episode) = withContext(Dispatchers.IO) {
        val url = episode.chaptersUrl ?: return@withContext
        if (chapterDao.count(episode.id) > 0) return@withContext
        try {
            val json = http.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
            chapterDao.insertAll(ChaptersJson.parse(json).map { Chapter(episode.id, it.startMs, it.title) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Chapters are optional; the episode plays fine without them.
            Log.w(LOG_TAG, "Loading chapters from $url failed", e)
        }
    }

    suspend fun enqueue(episodeId: Long) {
        val last = episodeDao.maxQueuePosition() ?: 0
        episodeDao.setQueuePosition(episodeId, last + 1)
    }

    suspend fun playNext(episodeId: Long) {
        val first = episodeDao.minQueuePosition() ?: 0
        episodeDao.setQueuePosition(episodeId, first - 1)
    }

    suspend fun removeFromQueue(episodeId: Long) = episodeDao.setQueuePosition(episodeId, null)

    /** Stores the queue in the given order. */
    suspend fun reorderQueue(episodeIds: List<Long>) = db.withTransaction {
        episodeIds.forEachIndexed { index, id -> episodeDao.setQueuePosition(id, index) }
    }

    suspend fun setPlayed(episodeId: Long, played: Boolean) {
        episodeDao.setPlayed(episodeId, played)
        if (!played) return
        episodeDao.setQueuePosition(episodeId, null)
        val episode = episodeDao.get(episodeId) ?: return
        if (settings.current().deletePlayed) downloads.delete(episode)
    }

    suspend fun markAllPlayed(podcastId: Long) = episodeDao.markAllPlayed(podcastId)

    data class ImportResult(val imported: Int, val failed: Int)

    suspend fun importOpml(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        var imported = 0
        var failed = 0
        for (entry in Opml.read(input)) {
            try {
                val id = preview(entry.feedUrl)
                subscribe(id)
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Skip feeds that are gone; import the rest.
                Log.w(LOG_TAG, "Importing ${entry.feedUrl} failed", e)
                failed++
            }
        }
        ImportResult(imported, failed)
    }

    suspend fun exportOpml(output: OutputStream) = withContext(Dispatchers.IO) {
        val entries = podcastDao.getSubscribed().map { OpmlEntry(it.title, it.feedUrl) }
        Opml.write(entries, output)
    }

    private suspend fun insertEpisodes(podcastId: Long, parsed: List<ParsedEpisode>): List<Episode> {
        val episodes = parsed.map { it.toEpisode(podcastId) }
        val ids = episodeDao.insertAll(episodes)
        val inserted = mutableListOf<Episode>()
        ids.forEachIndexed { index, id ->
            if (id == -1L) return@forEachIndexed
            inserted += episodes[index].copy(id = id)
            val chapters = parsed[index].chapters.map { Chapter(id, it.startMs, it.title) }
            if (chapters.isNotEmpty()) chapterDao.insertAll(chapters)
        }
        return inserted
    }

    private fun fetchFeed(url: String): ParsedFeed {
        val request = Request.Builder().url(url).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            FeedParser.parse(response.body.byteStream())
        }
    }

    private fun ParsedFeed.toPodcast(feedUrl: String) = Podcast(
        feedUrl = feedUrl,
        title = title.ifEmpty { feedUrl },
        author = author,
        description = description,
        imageUrl = imageUrl,
        link = link,
    )

    private fun ParsedEpisode.toEpisode(podcastId: Long) = Episode(
        podcastId = podcastId,
        guid = guid,
        title = title,
        description = description,
        pubDate = pubDate,
        mediaUrl = mediaUrl,
        mimeType = mimeType,
        fileSize = fileSize,
        duration = duration,
        imageUrl = imageUrl,
        chaptersUrl = chaptersUrl,
    )

    companion object {
        /** Episodes published within this time count as new. */
        private val NEW_EPISODE_WINDOW = TimeUnit.DAYS.toMillis(30)

        private fun newSince() = System.currentTimeMillis() - NEW_EPISODE_WINDOW
    }
}
