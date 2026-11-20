package io.github.sj14.lauscher.data

import android.util.Log
import androidx.room.withTransaction
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.data.db.AppDatabase
import io.github.sj14.lauscher.data.db.Chapter
import io.github.sj14.lauscher.data.db.Episode
import io.github.sj14.lauscher.data.db.Podcast
import io.github.sj14.lauscher.data.db.ShowNotes
import io.github.sj14.lauscher.data.feed.ChaptersJson
import io.github.sj14.lauscher.data.feed.FeedParser
import io.github.sj14.lauscher.data.feed.ParsedChapter
import io.github.sj14.lauscher.data.feed.ParsedEpisode
import io.github.sj14.lauscher.data.feed.ParsedFeed
import io.github.sj14.lauscher.data.feed.ShowNotesChapters
import io.github.sj14.lauscher.download.Downloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

class PodcastRepository(
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val downloads: Downloads,
) {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()
    private val showNotesDao = db.showNotesDao()
    private val chapterDao = db.chapterDao()

    /** The time from which on an episode counts as new, following the setting. */
    private fun newSince(): Flow<Long> = settings.settings
        .map { it.newEpisodeDays }
        .distinctUntilChanged()
        .map { days -> System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeLibrary() = newSince().flatMapLatest { podcastDao.observeSubscribed(it) }
    fun observePodcast(id: Long) = podcastDao.observe(id)
    fun observeEpisodes(podcastId: Long) = episodeDao.observeForPodcast(podcastId)
    fun observeEpisode(id: Long) = episodeDao.observeWithPodcast(id)
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeNew() = newSince().flatMapLatest { episodeDao.observeNew(it) }
    fun observeQueue() = episodeDao.observeQueue()
    fun observeDownloads() = episodeDao.observeDownloads()
    fun observeShowNotes(episodeId: Long) = showNotesDao.observe(episodeId)
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
        // Also stops downloads that are still running, so they leave no file behind.
        for (episode in episodeDao.getWithDownloadForPodcast(podcastId)) {
            downloads.cancel(episode.id)
        }
        podcastDao.delete(podcastId)
    }

    suspend fun deleteUnsubscribed() = podcastDao.deleteUnsubscribed()

    /**
     * Refreshes one podcast and downloads its new episodes if enabled. A podcast that is only
     * previewed from the search can be refreshed too, but nothing is downloaded for it.
     */
    suspend fun refreshAndDownload(podcast: Podcast) {
        val newEpisodes = refresh(podcast)
        val current = settings.current()
        if (current.autoDownload && podcast.subscribed) {
            newEpisodes.forEach { downloads.enqueue(it.id, current.wifiOnly) }
        }
    }

    /** Refreshes all subscriptions and downloads new episodes if enabled. Returns the number of failed feeds. */
    suspend fun refreshAll(): Int {
        var failures = 0
        for (podcast in podcastDao.getSubscribed()) {
            try {
                refreshAndDownload(podcast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Refreshing ${podcast.feedUrl} failed", e)
                failures++
            }
        }
        return failures
    }

    /** Refreshes one podcast and returns the episodes that are new since the last refresh. */
    private suspend fun refresh(podcast: Podcast): List<Episode> = withContext(Dispatchers.IO) {
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

    /**
     * Loads the chapters of an episode when it starts playing, unless it has some already from the
     * feed. They come from the chapter file the feed links to, or else from a table of contents in
     * the show notes.
     */
    suspend fun loadChapters(episode: Episode) = withContext(Dispatchers.IO) {
        if (chapterDao.count(episode.id) > 0) return@withContext
        var chapters = emptyList<ParsedChapter>()
        val url = episode.chaptersUrl
        if (url != null) {
            // If the file cannot be loaded now, the next start tries again instead of settling
            // for the show notes.
            chapters = loadChaptersFile(url) ?: return@withContext
        }
        if (chapters.isEmpty()) {
            chapters = ShowNotesChapters.parse(showNotesDao.get(episode.id).orEmpty())
        }
        chapterDao.insertAll(chapters.map { Chapter(episode.id, it.startMs, it.title, it.imageUrl) })
    }

    /** The chapters in a Podcasting 2.0 chapter file, null if it could not be loaded. */
    private fun loadChaptersFile(url: String): List<ParsedChapter>? {
        try {
            val request = Request.Builder().url(url).build()
            val json = http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                response.body.string()
            }
            return ChaptersJson.parse(json)
        } catch (e: Exception) {
            // Chapters are optional; the episode plays fine without them.
            Log.w(LOG_TAG, "Loading chapters from $url failed", e)
            return null
        }
    }

    /** Adds the episode at the top or bottom of the queue, as chosen in the settings. */
    suspend fun enqueue(episodeId: Long) {
        when (settings.current().queueAddPosition) {
            QueueAddPosition.TOP -> playNext(episodeId)
            QueueAddPosition.BOTTOM -> {
                val last = episodeDao.maxQueuePosition() ?: 0
                episodeDao.setQueuePosition(episodeId, last + 1)
            }
        }
    }

    /** Puts a playing episode at the top of the queue, unless it is already queued. */
    suspend fun ensureQueued(episodeId: Long) {
        val episode = episodeDao.get(episodeId) ?: return
        if (episode.queuePosition == null) playNext(episodeId)
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

    private suspend fun insertEpisodes(podcastId: Long, parsed: List<ParsedEpisode>): List<Episode> {
        val episodes = parsed.map { it.toEpisode(podcastId) }
        val ids = episodeDao.insertAll(episodes)
        val inserted = mutableListOf<Episode>()
        val showNotes = mutableListOf<ShowNotes>()
        ids.forEachIndexed { index, id ->
            if (id == -1L) return@forEachIndexed
            inserted += episodes[index].copy(id = id)
            val description = parsed[index].description
            if (description.isNotEmpty()) showNotes += ShowNotes(id, description)
            val chapters = parsed[index].chapters.map { Chapter(id, it.startMs, it.title, it.imageUrl) }
            if (chapters.isNotEmpty()) chapterDao.insertAll(chapters)
        }
        showNotesDao.insertAll(showNotes)
        return inserted
    }

    /** Some servers occasionally send broken data, so a network error is retried a few times. */
    private fun fetchFeed(url: String): ParsedFeed {
        var attempt = 1
        while (true) {
            try {
                return fetchFeedOnce(url)
            } catch (e: IOException) {
                if (attempt >= FEED_ATTEMPTS) throw e
                Log.i(LOG_TAG, "Loading $url failed, trying again", e)
                attempt++
            }
        }
    }

    private fun fetchFeedOnce(url: String): ParsedFeed {
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
        pubDate = pubDate,
        mediaUrl = mediaUrl,
        mimeType = mimeType,
        fileSize = fileSize,
        duration = duration,
        imageUrl = imageUrl,
        chaptersUrl = chaptersUrl,
    )

    companion object {
        private const val FEED_ATTEMPTS = 3
    }
}
