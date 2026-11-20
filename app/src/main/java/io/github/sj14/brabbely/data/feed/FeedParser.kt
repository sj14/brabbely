package io.github.sj14.brabbely.data.feed

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedChapter(val startMs: Long, val title: String, val imageUrl: String? = null)

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
    private const val RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

    fun parse(input: InputStream): ParsedFeed {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // Some feeds contain unescaped HTML, such as a stray </p> in a description. The relaxed
        // mode of Android's parser accepts that instead of failing on the whole feed.
        parser.setFeature(RELAXED, true)
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
        return ParsedFeed(
            title = decodeEntities(title.trim()),
            author = decodeEntities(author.trim()),
            description = description.trim(),
            imageUrl = imageUrl,
            link = link,
            episodes = episodes,
        )
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
                // The standard attribute is url; Podigee writes href instead.
                ns == NS_PODCAST && parser.name == "chapters" ->
                    chaptersUrl = parser.getAttributeValue(null, "url") ?: parser.getAttributeValue(null, "href")
                ns == NS_PSC && parser.name == "chapter" -> {
                    val start = parseDuration(parser.getAttributeValue(null, "start").orEmpty())
                    val title = decodeEntities(parser.getAttributeValue(null, "title").orEmpty())
                    chapters += ParsedChapter(start, title, imageUrl = parser.getAttributeValue(null, "image"))
                }
            }
        }

        val url = mediaUrl ?: return null
        return ParsedEpisode(
            guid = guid?.trim()?.ifEmpty { null } ?: url,
            title = decodeEntities(title.trim()),
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

    private val entityPattern = Regex("&(#[0-9]+|#[xX][0-9a-fA-F]+|amp|lt|gt|quot|apos);")

    /**
     * Decodes HTML entities that are left in a text after the XML parser decoded it once. Some
     * feeds escape their titles twice, so "Rock &amp;amp; Roll" would otherwise show as
     * "Rock &amp; Roll". Only plain-text fields go through this, not the HTML show notes.
     */
    internal fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        return entityPattern.replace(text) { match ->
            val entity = match.groupValues[1]
            when {
                entity == "amp" -> "&"
                entity == "lt" -> "<"
                entity == "gt" -> ">"
                entity == "quot" -> "\""
                entity == "apos" -> "'"
                entity.startsWith("#x") || entity.startsWith("#X") -> codePointToString(entity.drop(2).toIntOrNull(16), match.value)
                else -> codePointToString(entity.drop(1).toIntOrNull(), match.value)
            }
        }
    }

    /** The character of a numeric entity, or the entity unchanged if the number is no valid character. */
    private fun codePointToString(codePoint: Int?, entity: String): String {
        if (codePoint == null || !Character.isValidCodePoint(codePoint)) return entity
        return String(Character.toChars(codePoint))
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
