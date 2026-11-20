package io.github.sj14.brabbely.data.feed

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

data class SearchResult(
    val title: String,
    val author: String,
    val feedUrl: String,
    val imageUrl: String?,
)

/** Searches podcasts with the public iTunes Search API, which needs no API key. */
class PodcastSearch(private val http: OkHttpClient) {
    fun search(term: String): List<SearchResult> {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("media", "podcast")
            .addQueryParameter("entity", "podcast")
            .addQueryParameter("limit", "50")
            .addQueryParameter("term", term)
            .build()
        val body = http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.string()
        }

        val results = JSONObject(body).optJSONArray("results") ?: return emptyList()
        val podcasts = mutableListOf<SearchResult>()
        for (index in 0 until results.length()) {
            val item = results.getJSONObject(index)
            val feedUrl = item.optString("feedUrl")
            if (feedUrl.isEmpty()) continue
            podcasts += SearchResult(
                title = item.optString("collectionName"),
                author = item.optString("artistName"),
                feedUrl = feedUrl,
                imageUrl = item.optString("artworkUrl600").ifEmpty { null },
            )
        }
        // iTunes sometimes lists the same feed more than once.
        return podcasts.distinctBy { it.feedUrl }
    }
}
