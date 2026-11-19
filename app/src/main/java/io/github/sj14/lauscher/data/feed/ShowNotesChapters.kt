package io.github.sj14.lauscher.data.feed

/**
 * Reads chapters from a table of contents in the show notes, which many podcasts write instead of
 * real chapter marks:
 *
 *     00:00:00 Intro
 *     00:09:43 Quantenkonzert und Teamwork
 *
 * Only lines that start with a time followed by a title count. There have to be at least two, and
 * their times have to go up; otherwise the times belong to something else and there are no chapters.
 */
object ShowNotesChapters {
    /** Tags that end a line, so a table of contents in HTML becomes one entry per line. */
    private val LINE_END = Regex("""<br\s*/?>|</(p|div|li|h\d)>""", RegexOption.IGNORE_CASE)
    private val TAG = Regex("""<[^>]*>""")
    private val NUMERIC_ENTITY = Regex("""&#(x?)([0-9a-fA-F]+);""")

    /** "00:09:43 Title", "9:43 - Title" or "[00:09:43] Title". */
    private val ENTRY = Regex("""[\[(]?((?:\d{1,2}:)?\d{1,2}:\d{2})[\])]?\s*[-–—:|]?\s+(\S.*)""")

    private const val MIN_CHAPTERS = 2

    fun parse(showNotes: String): List<ParsedChapter> {
        val text = showNotes.replace(LINE_END, "\n").replace(TAG, "")
        val chapters = mutableListOf<ParsedChapter>()
        for (line in text.lines()) {
            val match = ENTRY.matchEntire(decodeEntities(line).trim()) ?: continue
            val (time, title) = match.destructured
            val startMs = FeedParser.parseDuration(time)
            if (chapters.isNotEmpty() && startMs <= chapters.last().startMs) return emptyList()
            chapters += ParsedChapter(startMs, title.trim())
        }
        return if (chapters.size >= MIN_CHAPTERS) chapters else emptyList()
    }

    /** The few HTML entities that turn up in chapter titles; a no-break space becomes a normal one. */
    private fun decodeEntities(text: String): String {
        val decoded = NUMERIC_ENTITY.replace(text) { match ->
            val (hex, digits) = match.destructured
            val codePoint = digits.toIntOrNull(if (hex.isEmpty()) 10 else 16)
            if (codePoint == null) match.value else String(Character.toChars(codePoint))
        }
        return decoded
            .replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(' ', ' ')
    }
}
