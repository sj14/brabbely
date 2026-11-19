package io.github.sj14.lauscher.data.backup

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.sj14.lauscher.LOG_TAG
import io.github.sj14.lauscher.LauscherApp
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/** Writes a backup into the folder chosen in the settings once a day and keeps the newest ones. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as LauscherApp).container
        val folderUri = container.settings.current().autoBackupFolder ?: return Result.success()
        val folder = DocumentFile.fromTreeUri(applicationContext, folderUri.toUri())
        if (folder == null || !folder.canWrite()) {
            Log.w(LOG_TAG, "Backup folder $folderUri is not writable")
            return Result.failure()
        }

        val name = BackupFormat.fileName(LocalDate.now())
        // A second backup on the same day replaces the first one.
        folder.findFile(name)?.delete()
        val file = folder.createFile(BackupFormat.MIME_TYPE, name) ?: return Result.retry()
        try {
            applicationContext.contentResolver.openOutputStream(file.uri)!!.use { container.backups.createBackup(it) }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Automatic backup failed", e)
            file.delete()
            return Result.retry()
        }

        deleteOldBackups(folder)
        return Result.success()
    }

    /** Deletes all but the newest backups. Only files named like the app's own backups are touched. */
    private fun deleteOldBackups(folder: DocumentFile) {
        val backups = folder.listFiles()
            .filter { it.isFile && it.name?.matches(BACKUP_NAME) == true }
            .sortedByDescending { it.name }
        backups.drop(BACKUPS_TO_KEEP).forEach { it.delete() }
    }

    companion object {
        private const val NAME = "auto-backup"
        private const val BACKUPS_TO_KEEP = 7
        private val BACKUP_NAME = Regex("""lauscher-backup-\d{4}-\d{2}-\d{2}\.json\.gz""")

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS).build()
            workManager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
