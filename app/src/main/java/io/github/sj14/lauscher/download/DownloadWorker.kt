package io.github.sj14.lauscher.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.LauscherApp
import io.github.sj14.lauscher.R
import io.github.sj14.lauscher.data.db.DownloadState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

class DownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val container = (context.applicationContext as LauscherApp).container

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val episodeId = inputData.getLong(KEY_EPISODE_ID, -1)
        val episodeDao = container.database.episodeDao()
        val episode = episodeDao.get(episodeId) ?: return@withContext Result.failure()

        setForeground(foregroundInfo(episode.title, 0))
        episodeDao.setDownload(episodeId, DownloadState.DOWNLOADING, 0, null)

        val target = File(container.downloads.directory, "$episodeId.${extensionFor(episode.mimeType)}")
        val partial = File(target.path + ".part")
        try {
            val request = Request.Builder().url(episode.mediaUrl).build()
            container.http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val total = response.body.contentLength()
                var written = 0L
                var lastReported = 0L
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            written += read
                            if (written - lastReported > REPORT_EVERY_BYTES) {
                                lastReported = written
                                episodeDao.setDownloadProgress(episodeId, written, total)
                                val percent = if (total > 0) (written * 100 / total).toInt() else 0
                                setForeground(foregroundInfo(episode.title, percent))
                            }
                        }
                        // Make sure the data is on disk before the download counts as done.
                        output.fd.sync()
                    }
                }
            }
            partial.renameTo(target)
            episodeDao.setDownload(episodeId, DownloadState.DONE, target.length(), target.name)
            Result.success()
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Downloading ${episode.mediaUrl} failed", e)
            partial.delete()
            if (isStopped) {
                // Either cancelled by the user, then Downloads.cancel already reset the state, or stopped
                // by the system, for example because Wi-Fi was lost, and WorkManager runs it again later.
                // The coroutine is cancelled by now, so the database is only reachable without cancellation.
                withContext(NonCancellable) {
                    if (episodeDao.get(episodeId)?.downloadState == DownloadState.DOWNLOADING) {
                        episodeDao.setDownload(episodeId, DownloadState.QUEUED, 0, null)
                    }
                }
                Result.failure()
            } else if (runAttemptCount < MAX_ATTEMPTS) {
                episodeDao.setDownload(episodeId, DownloadState.QUEUED, 0, null)
                Result.retry()
            } else {
                episodeDao.setDownload(episodeId, DownloadState.FAILED, 0, null)
                Result.failure()
            }
        }
    }

    private fun foregroundInfo(title: String, percent: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notification_channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(applicationContext.getString(R.string.downloading))
            .setContentText(title)
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setSilent(true)
            .build()
        val notificationId = NOTIFICATION_BASE_ID + Math.floorMod(id.hashCode(), 1000)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun extensionFor(mimeType: String) = when (mimeType) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "audio/mp4", "audio/x-m4a", "audio/aac" -> "m4a"
        "audio/ogg", "audio/opus" -> "ogg"
        else -> "mp3"
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val TAG = "download"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIFICATION_BASE_ID = 2000
        private const val MAX_ATTEMPTS = 3
        private const val REPORT_EVERY_BYTES = 512 * 1024
    }
}
