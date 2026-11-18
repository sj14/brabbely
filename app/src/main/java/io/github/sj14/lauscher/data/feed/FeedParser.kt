package io.github.sj14.lauscher.data.feed

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedChapter(val startMs: Long, val title: String)

data class ParsedEpisode(
    val guid: String,
    val title: String,
    val description: String,
    val pubDate: Long,
    val mediaUrl: String,
    val mimeType: String,
    val fileSize: Long,
    val duration: Long,
    val imageUrl: String?,
    val chaptersUrl: String?,
    val chapters: List<ParsedChapter>,
)

data class ParsedFeed(
    val title: String,
    val author: String,
    val description: String,
    val imageUrl: String?,
    val link: String?,
    val episodes: List<ParsedEpisode>,
)

/** Parses RSS 2.0 podcast feeds including the common iTunes, Podlove and Podcasting 2.0 tags. */
object FeedParser {
    private const val NS_ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private const val NS_CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val NS_PSC = "http://podlove.org/simple-chapters"
    private const val NS_PODCAST = "https://podcastindex.org/namespace/1.0"

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(input, null)

        var title = ""
        var author = ""
        var description = ""
        var imageUrl: String? = null
        var link: String? = null
        val episodes = mutableListOf<ParsedEpisode>()

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType != XmlPullParser.START_TAG) continue
            when {
                parser.name == "item" -> parseItem(parser)?.let(episodes::add)
                parser.namespace.isEmpty() && parser.name == "title" && parser.depth == 3 -> title = parser.readText()
                parser.namespace.isEmpty() && parser.name == "link" && parser.depth == 3 -> link = parser.readText()
                parser.namespace.isEmpty() && parser.name == "description" && parser.depth == 3 ->
                    description = parser.readText()
                parser.namespace == NS_ITUNES && parser.name == "summary" && description.isEmpty() ->
                    description = parser.readText()
                parser.namespace == NS_ITUNES && parser.name == "author" -> author = parser.readText()
                parser.namespace == NS_ITUNES && parser.name == "image" ->
                    parser.getAttributeValue(null, "href")?.let { imageUrl = it }
                parser.namespace.isEmpty() && parser.name == "image" && imageUrl == null ->
                    imageUrl = parseRssImage(parser)
            }
        }
        return ParsedFeed(title.trim(), author.trim(), description.trim(), imageUrl, link, episodes)
    }

    private fun parseRssImage(parser: XmlPullParser): String? {
        var url: String? = null
        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "url") url = parser.readText()
        }
        return url
    }

    private fun parseItem(parser: XmlPullParser): ParsedEpisode? {
        var guid: String? = null
        var title = ""
        var description = ""
        var contentEncoded = ""
        var pubDate = 0L
        var mediaUrl: String? = null
        var mimeType = ""
        var fileSize = 0L
        var duration = 0L
        var imageUrl: String? = null
        var chaptersUrl: String? = null
        val chapters = mutableListOf<ParsedChapter>()

        val depth = parser.depth
        while (!(parser.next() == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (parser.eventType == XmlPullParser.END_DOCUMENT) break
            if (parser.eventType != XmlPullParser.START_TAG) continue
            val ns = parser.namespace
            when {
                ns.isEmpty() && parser.name == "guid" -> guid = parser.readText()
                ns.isEmpty() && parser.name == "title" -> title = parser.readText()
                ns.isEmpty() && parser.name == "description" -> description = parser.readText()
                ns == NS_CONTENT && parser.name == "encoded" -> contentEncoded = parser.readText()
                ns == NS_ITUNES && parser.name == "summary" && description.isEmpty() -> description = parser.readText()
                ns.isEmpty() && parser.name == "pubDate" -> pubDate = parseDate(parser.readText())
                ns.isEmpty() && parser.name == "enclosure" -> {
                    mediaUrl = parser.getAttributeValue(null, "url")
                    mimeType = parser.getAttributeValue(null, "type").orEmpty()
                    fileSize = parser.getAttributeValue(null, "length")?.trim()?.toLongOrNull() ?: 0
                }
                ns == NS_ITUNES && parser.name == "duration" -> duration = parseDuration(parser.readText())
                ns == NS_ITUNES && parser.name == "image" -> imageUrl = parser.getAttributeValue(null, "href")
                ns == NS_PODCAST && parser.name == "chapters" -> chaptersUrl = parser.getAttributeValue(null, "url")
                ns == NS_PSC && parser.name == "chapter" -> {
                    val start = parseDuration(parser.getAttributeValue(null, "start").orEmpty())
                    chapters += ParsedChapter(start, parser.getAttributeValue(null, "title").orEmpty())
                }
            }
        }

        val url = mediaUrl ?: return null
        return ParsedEpisode(
            guid = guid?.trim()?.ifEmpty { null } ?: url,
            title = title.trim(),
            description = contentEncoded.ifBlank { description }.trim(),
            pubDate = pubDate,
            mediaUrl = url,
            mimeType = mimeType.ifEmpty { guessMimeType(url) },
            fileSize = fileSize,
            duration = duration,
            imageUrl = imageUrl,
            chaptersUrl = chaptersUrl,
            chapters = chapters,
        )
    }

    private fun XmlPullParser.readText(): String {
        val text = StringBuilder()
        val depth = depth
        while (!(next() == XmlPullParser.END_TAG && this.depth == depth)) {
            if (eventType == XmlPullParser.TEXT || eventType == XmlPullParser.CDSECT) text.append(this.text)
            if (eventType == XmlPullParser.END_DOCUMENT) break
        }
        return text.toString()
    }

    private val dateFormats = listOf(
        DateTimeFormatter.RFC_1123_DATE_TIME,
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss zzz", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm zzz", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy HH:mm:ss Z", Locale.ENGLISH),
        DateTimeFormatter.ISO_OFFSET_DATE_TIME,
    )

    internal fun parseDate(text: String): Long {
        val value = text.trim().replace(Regex("\\s+"), " ")
        for (format in dateFormats) {
            try {
                return ZonedDateTime.parse(value, format).toInstant().toEpochMilli()
            } catch (_: Exception) {
                // try the next format
            }
        }
        return 0
    }

    /** Parses "1:02:03", "62:03", "3723" or "3723.5" into milliseconds. */
    internal fun parseDuration(text: String): Long {
        val parts = text.trim().split(":")
        var seconds = 0.0
        for (part in parts) {
            val value = part.toDoubleOrNull() ?: return 0
            seconds = seconds * 60 + value
        }
        return (seconds * 1000).toLong()
    }

    private fun guessMimeType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
            path.endsWith(".webm") -> "video/webm"
            path.endsWith(".m4a") -> "audio/mp4"
            path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
            else -> "audio/mpeg"
        }
    }
}
