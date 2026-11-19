package io.github.sj14.lauscher.data.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowNotesChaptersTest {
    private fun at(hours: Int, minutes: Int, seconds: Int) = ((hours * 60L + minutes) * 60 + seconds) * 1000

    @Test
    fun readsTableOfContentsFromHtml() {
        // From the show notes of "Methodisch inkorrekt!", episode 416.
        val html = """
            <p>Alle Werbepartner findet ihr <a href="https://linktr.ee/methodischinkorrekt">in diesem Linktree</a>!</p>
            <!-- wp:paragraph -->
            <p><strong>Inhalt</strong><br>
            00:00:00 Intro<br>
            00:09:43 Quantenkonzert und Teamwork<br>
            01:42:36 Science Snack<br>
            02:04:02 Kommentar Sandra zum Krallenfrosch</p>
            <h2 class="wp-block-heading">Einleitung &amp; Begrüßung</h2>
            <ul><li><strong>Intro:</strong> Das Intro dreht sich um den „Podcastdeckel“.</li></ul>
        """
        assertEquals(
            listOf(
                ParsedChapter(0, "Intro"),
                ParsedChapter(at(0, 9, 43), "Quantenkonzert und Teamwork"),
                ParsedChapter(at(1, 42, 36), "Science Snack"),
                ParsedChapter(at(2, 4, 2), "Kommentar Sandra zum Krallenfrosch"),
            ),
            ShowNotesChapters.parse(html),
        )
    }

    @Test
    fun readsPlainTextWithOtherSeparators() {
        val text = "Themen:\n[0:00] Start\n12:30 - Fragen &amp; Antworten\n(1:02:03) Ende"
        assertEquals(
            listOf(
                ParsedChapter(0, "Start"),
                ParsedChapter(at(0, 12, 30), "Fragen & Antworten"),
                ParsedChapter(at(1, 2, 3), "Ende"),
            ),
            ShowNotesChapters.parse(text),
        )
    }

    @Test
    fun noBreakSpaceSeparatesTimeAndTitle() {
        val html = "<p>00:00:00&nbsp;Intro<br>00:05:00 Thema</p>"
        assertEquals(
            listOf(ParsedChapter(0, "Intro"), ParsedChapter(at(0, 5, 0), "Thema")),
            ShowNotesChapters.parse(html),
        )
    }

    @Test
    fun aSingleTimeIsNoTableOfContents() {
        assertEquals(emptyList<ParsedChapter>(), ShowNotesChapters.parse("<p>00:12:00 Werbung</p><p>Viel Spaß!</p>"))
    }

    @Test
    fun timesInsideSentencesAreIgnored() {
        val text = "Wir treffen uns um 19:30 Uhr.\nDie Folge beginnt bei 00:01:00 mit einem Rätsel."
        assertEquals(emptyList<ParsedChapter>(), ShowNotesChapters.parse(text))
    }

    @Test
    fun timesThatDoNotGoUpAreNoChapters() {
        val text = "18:00 Einlass\n20:00 Beginn\n19:00 Pause"
        assertEquals(emptyList<ParsedChapter>(), ShowNotesChapters.parse(text))
    }
}
