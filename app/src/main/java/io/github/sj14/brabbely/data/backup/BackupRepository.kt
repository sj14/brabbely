package io.github.sj14.brabbely.data.backup

import android.util.Log
import androidx.room.withTransaction
import io.github.sj14.brabbely.LOG_TAG
import io.github.sj14.brabbely.data.PodcastRepository
import io.github.sj14.brabbely.data.SettingsRepository
import io.github.sj14.brabbely.data.db.AppDatabase
import io.github.sj14.brabbely.data.opml.Opml
import io.github.sj14.brabbely.data.opml.OpmlEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/** Backups of the app and the import and export of subscriptions from and to other apps. */
class BackupRepository(
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val podcasts: PodcastRepository,
    /** Directory for temporary files, such as an import that has to be copied first. */
    private val tempDir: File,
) {
    private val podcastDao = db.podcastDao()
    private val episodeDao = db.episodeDao()

    data class ImportResult(val imported: Int, val failed: Int)

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

    suspend fun importOpml(input: InputStream): ImportResult = withContext(Dispatchers.IO) {
        subscribeAll(Opml.read(input).map { it.feedUrl })
    }

    suspend fun exportOpml(output: OutputStream) = withContext(Dispatchers.IO) {
        val entries = podcastDao.getSubscribed().map { OpmlEntry(it.title, it.feedUrl) }
        Opml.write(entries, output)
    }

    /** Subscribes to the podcasts of a backup and restores what was listened to and the settings. */
    private suspend fun restore(backup: Backup): ImportResult {
        settings.import(backup.settings)

        val podcastsByUrl = backup.podcasts.associateBy { it.feedUrl }
        // Episode ids by feed URL and GUID, to rebuild the queue at the end.
        val episodeIds = mutableMapOf<String, Map<String, Long>>()
        val result = subscribeAll(backup.podcasts.map { it.feedUrl }) { feedUrl, podcastId ->
            val ids = episodeDao.getKeys(podcastId).associate { it.guid to it.id }
            restoreProgress(podcastsByUrl.getValue(feedUrl), ids)
            episodeIds[feedUrl] = ids
        }

        val queue = backup.queue.mapNotNull { episodeIds[it.feedUrl]?.get(it.guid) }
        db.withTransaction {
            episodeDao.clearQueue()
            queue.forEachIndexed { index, id -> episodeDao.setQueuePosition(id, index) }
        }
        return result
    }

    /**
     * Subscribes to each feed and then runs [afterSubscribe] for it. A feed that fails, for example
     * because it is gone, is skipped and counted; the others are still imported.
     */
    private suspend fun subscribeAll(
        feedUrls: List<String>,
        afterSubscribe: suspend (feedUrl: String, podcastId: Long) -> Unit = { _, _ -> },
    ): ImportResult {
        var imported = 0
        var failed = 0
        for (feedUrl in feedUrls) {
            try {
                val podcastId = podcasts.preview(feedUrl)
                podcasts.subscribe(podcastId)
                afterSubscribe(feedUrl, podcastId)
                imported++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LOG_TAG, "Importing $feedUrl failed", e)
                failed++
            }
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
}
