package io.github.sj14.brabbely.data.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import io.github.sj14.brabbely.R

/**
 * Lists the podcasts whose feed could not be loaded during an import. A toast shows only two
 * lines and disappears, so the names go into a notification that stays until it is read.
 */
object ImportFailedNotification {
    private const val CHANNEL_ID = "import"
    private const val NOTIFICATION_ID = 3000

    fun show(context: Context, failedTitles: List<String>) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_import),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)

        val title = context.resources.getQuantityString(
            R.plurals.import_failed_feeds,
            failedTitles.size,
            failedTitles.size,
        )
        val text = failedTitles.joinToString(", ") + "\n\n" + context.getString(R.string.import_again_hint)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(failedTitles.joinToString(", "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        // Without the permission to post notifications this does nothing; the toast still
        // tells how many feeds failed.
        manager.notify(NOTIFICATION_ID, notification)
    }
}
