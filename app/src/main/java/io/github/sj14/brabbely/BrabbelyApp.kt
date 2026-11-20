package io.github.sj14.brabbely

import android.app.Application
import android.app.backup.BackupManager
import android.content.Context
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.sj14.brabbely.data.PodcastRepository
import io.github.sj14.brabbely.data.SettingsRepository
import io.github.sj14.brabbely.data.backup.AutoBackupWorker
import io.github.sj14.brabbely.data.backup.BackupRepository
import io.github.sj14.brabbely.data.backup.SystemBackupAgent
import io.github.sj14.brabbely.data.db.AppDatabase
import io.github.sj14.brabbely.data.feed.PodcastSearch
import io.github.sj14.brabbely.download.Downloads
import io.github.sj14.brabbely.playback.PlayerConnection
import io.github.sj14.brabbely.playback.SleepTimer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Tag for all log messages of the app. */
const val LOG_TAG = "Brabbely"

/** Creates and holds the objects that live as long as the app. */
class AppContainer(context: Context) {
    val appScope: CoroutineScope = MainScope()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Brabbely/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    val database = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val downloads = Downloads(context, database.episodeDao())
    val repository = PodcastRepository(database, http, settings, downloads)
    val backups = BackupRepository(database, settings, repository, context.cacheDir)
    val search = PodcastSearch(http)
    val sleepTimer = SleepTimer(context, appScope)
    val playerConnection = PlayerConnection(context)
}

class BrabbelyApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            // Podcasts that were only previewed in the search are not kept. This runs before the
            // restore, which previews each podcast before subscribing to it.
            container.repository.deleteUnsubscribed()
            restoreSystemBackup()
        }
        // Lets Android back up what changed since the app was last used, see SystemBackupAgent.
        BackupManager(this).dataChanged()
        container.appScope.launch {
            container.settings.settings
                .map { it.refreshIntervalHours }
                .distinctUntilChanged()
                .collect { container.downloads.scheduleRefresh(it) }
        }
        container.appScope.launch {
            container.settings.settings
                .map { it.autoBackupFolder != null }
                .distinctUntilChanged()
                .collect { AutoBackupWorker.schedule(this@BrabbelyApp, enabled = it) }
        }
    }

    /** Restores the backup that Android's backup brought onto a new device, if any. */
    private suspend fun restoreSystemBackup() {
        val file = SystemBackupAgent.pendingRestoreFile(this)
        if (!file.exists()) return
        try {
            val result = file.inputStream().use { container.backups.restoreBackup(it) }
            // Without a connection no feed could be loaded; then the next start tries again.
            if (result.imported == 0 && result.failed > 0) return
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A broken backup will not get better; trying again at every start would not help.
            Log.w(LOG_TAG, "Restoring the backup from Android's backup failed", e)
        }
        file.delete()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .crossfade(true)
            .build()
}
