package io.github.sj14.brabbely.data.feed

import org.json.JSONObject

/** Parses the JSON chapter format of the Podcasting 2.0 namespace (podcast:chapters). */
object ChaptersJson {
    fun parse(json: String): List<ParsedChapter> {
        val array = JSONObject(json).optJSONArray("chapters") ?: return emptyList()
        val chapters = mutableListOf<ParsedChapter>()
        for (index in 0 until array.length()) {
            val chapter = array.getJSONObject(index)
            // Chapters with toc = false are only meant to change artwork, not to be listed.
            if (!chapter.optBoolean("toc", true)) continue
            val startMs = (chapter.optDouble("startTime", 0.0) * 1000).toLong()
            val imageUrl = chapter.optString("img").ifEmpty { null }
            chapters += ParsedChapter(startMs, chapter.optString("title"), imageUrl)
        }
        return chapters
    }
}
