package io.github.sj14.lauscher

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import io.github.sj14.lauscher.data.PodcastRepository
import io.github.sj14.lauscher.data.SettingsRepository
import io.github.sj14.lauscher.data.db.AppDatabase
import io.github.sj14.lauscher.data.feed.PodcastSearch
import io.github.sj14.lauscher.download.Downloads
import io.github.sj14.lauscher.playback.PlayerConnection
import io.github.sj14.lauscher.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Tag for all log messages of the app. */
const val LOG_TAG = "Lauscher"

/** Creates and holds the objects that live as long as the app. */
class AppContainer(context: Context) {
    val appScope: CoroutineScope = MainScope()

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Lauscher/${BuildConfig.VERSION_NAME} (Android)")
                .build()
            chain.proceed(request)
        }
        .build()

    val database = AppDatabase.create(context)
    val settings = SettingsRepository(context)
    val downloads = Downloads(context, database.episodeDao())
    val repository = PodcastRepository(database, http, settings, downloads)
    val search = PodcastSearch(http)
    val sleepTimer = SleepTimer(context, appScope)
    val playerConnection = PlayerConnection(context)
}

class LauscherApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            // Podcasts that were only previewed in the search are not kept.
            container.repository.deleteUnsubscribed()
        }
        container.appScope.launch {
            container.settings.settings
                .map { it.refreshIntervalHours }
                .distinctUntilChanged()
                .collect { container.downloads.scheduleRefresh(it) }
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.http })) }
            .crossfade(true)
            .build()
}
