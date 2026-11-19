package io.github.sj14.lauscher.data.backup

import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.sj14.lauscher.LauscherApp
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Gives Android's own backup the app's compact backup instead of the database: with all the show
 * notes, the database soon exceeds the limit of 25 MB per app, and then nothing would be backed up
 * at all. Android's backup is whatever the device uses: the Google account on most phones,
 * Seedvault on GrapheneOS and other systems without Google, and the transfer to a new device.
 * The user turns it on in the Android settings, not in the app.
 *
 * On a new device the backup is restored before the app first starts, which is too early to load
 * the feeds. It is only stored then, and [LauscherApp] restores it at the next start.
 */
class SystemBackupAgent : BackupAgent() {
    override fun onBackup(oldState: ParcelFileDescriptor?, data: BackupDataOutput, newState: ParcelFileDescriptor) {
        val backups = (applicationContext as LauscherApp).container.backups
        val output = ByteArrayOutputStream()
        // Backup agents are called on a background thread and may block.
        runBlocking { backups.createBackup(output) }
        val bytes = output.toByteArray()
        data.writeEntityHeader(KEY, bytes.size)
        data.writeEntityData(bytes, bytes.size)
    }

    override fun onRestore(data: BackupDataInput, appVersionCode: Int, newState: ParcelFileDescriptor) {
        while (data.readNextHeader()) {
            if (data.key != KEY) {
                data.skipEntityData()
                continue
            }
            val bytes = ByteArray(data.dataSize)
            data.readEntityData(bytes, 0, bytes.size)
            pendingRestoreFile(this).writeBytes(bytes)
        }
    }

    companion object {
        private const val KEY = "backup"

        /** A restored backup that has not been applied yet. Not part of any backup itself. */
        fun pendingRestoreFile(context: Context) = File(context.noBackupFilesDir, "pending-restore.json.gz")
    }
}
