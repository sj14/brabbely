package io.github.sj14.lauscher.download

import android.content.Context
import android.os.Environment
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.sj14.lauscher.data.db.DownloadState
import io.github.sj14.lauscher.data.db.Episode
import io.github.sj14.lauscher.data.db.EpisodeDao
import java.io.File
import java.util.concurrent.TimeUnit

/** Schedules episode downloads and feed refreshes with WorkManager. */
class Downloads(private val context: Context, private val episodeDao: EpisodeDao) {
    private val workManager get() = WorkManager.getInstance(context)

    val directory: File
        get() = context.getExternalFilesDir(Environment.DIRECTORY_PODCASTS) ?: File(context.filesDir, "podcasts")

    suspend fun enqueue(episodeId: Long, wifiOnly: Boolean) {
        episodeDao.setDownload(episodeId, DownloadState.QUEUED, 0, null)
        val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(workDataOf(DownloadWorker.KEY_EPISODE_ID to episodeId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
            .addTag(DownloadWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(workName(episodeId), ExistingWorkPolicy.KEEP, request)
    }

    suspend fun cancel(episodeId: Long) {
        workManager.cancelUniqueWork(workName(episodeId))
        val episode = episodeDao.get(episodeId) ?: return
        delete(episode)
    }

    suspend fun delete(episode: Episode) {
        episode.localPath?.let { File(it).delete() }
        episodeDao.setDownload(episode.id, DownloadState.NONE, 0, null)
    }

    fun scheduleRefresh(intervalHours: Int) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniquePeriodicWork(RefreshWorker.NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refreshNow() {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().build()
        workManager.enqueueUniqueWork(RefreshWorker.NAME_NOW, ExistingWorkPolicy.KEEP, request)
    }

    private fun workName(episodeId: Long) = "download-$episodeId"
}
