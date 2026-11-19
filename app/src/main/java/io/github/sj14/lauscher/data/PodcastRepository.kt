package io.github.sj14.lauscher.data

import android.util.Log
import androidx.room.withTransaction
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.data.backup.AntennaPodImport
import io.github.sj14.lauscher.data.backup.Backup
import io.github.sj14.lauscher.data.backup.BackupFormat
import io.github.sj14.lauscher.data.backup.PodcastBackup
import io.github.sj14.lauscher.data.backup.QueueEntry
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
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

class PodcastRepository(
    private val db: AppDatabase,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    private val downloads: Downloads,
    /** Directory for temporary files, such as an import that has to be copied first. */
    private val tempDir: File,
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

    suspend fun createBackup(output: OutputStream) = withContext(Dispatchers.IO) {
        val subscribed = podcastDao.getSubscribed()
        val podcasts = subscribed.map { podcast ->
            val progress = episodeDao.getProgress(podcast.id)
            PodcastBackup(
                feedUrl = podcast.feedUrl,
                title = podcast.title,
                playedGuids = progress.filter { it.played }.map { it.guid },
                positions = progress.filter { !it.played }.associate { it.guid to it.position },
            )
        }
        val feedUrls = subscribed.associate { it.id to it.feedUrl }
        val queue = episodeDao.getQueue().mapNotNull { episode ->
            feedUrls[episode.podcastId]?.let { QueueEntry(it, episode.guid) }
        }
        BackupFormat.write(Backup(settings.export(), podcasts, queue), output)
    }

    suspend fun restoreBackup(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        restore(BackupFormat.read(input))
    }

    /** Imports the database export of AntennaPod. SQLite needs a file, so the export is copied first. */
    suspend fun importAntennaPod(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        val databaseFile = File(tempDir, "antennapod-import.db")
        try {
            databaseFile.outputStream().use { input.copyTo(it) }
            restore(AntennaPodImport.read(databaseFile))
        } finally {
            databaseFile.delete()
        }
    }

    /** Subscribes to the podcasts of a backup and restores what was listened to and the settings. */
    private suspend fun restore(backup: Backup): ImportResult {
        settings.import(backup.settings)

        var imported = 0
        var failed = 0
        // Episode ids by feed URL and GUID, to rebuild the queue at the end.
        val episodeIds = mutableMapOf<String, Map<String, Long>>()
        for (podcast in backup.podcasts) {
            try {
                val podcastId = preview(podcast.feedUrl)
                subscribe(podcastId)
                val ids = episodeDao.getKeys(podcastId).associate { it.guid to it.id }
                restoreProgress(podcast, ids)
                episodeIds[podcast.feedUrl] = ids
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Restoring ${podcast.feedUrl} failed", e)
                failed++
            }
        }

        val queue = backup.queue.mapNotNull { episodeIds[it.feedUrl]?.get(it.guid) }
        db.withTransaction {
            episodeDao.clearQueue()
            queue.forEachIndexed { index, id -> episodeDao.setQueuePosition(id, index) }
        }
        return ImportResult(imported, failed)
    }

    private suspend fun restoreProgress(podcast: PodcastBackup, episodeIds: Map<String, Long>) = db.withTransaction {
        for (guid in podcast.playedGuids) {
            val id = episodeIds[guid] ?: continue
            episodeDao.setPlayed(id, true)
        }
        for ((guid, position) in podcast.positions) {
            val id = episodeIds[guid] ?: continue
            episodeDao.setPosition(id, position, playedAt = 0)
        }
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
        private const val FEED_ATTEMPTS = 3

        private fun newSince() = System.currentTimeMillis() - NEW_EPISODE_WINDOW
    }
}
