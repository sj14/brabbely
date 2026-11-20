package io.github.sj14.brabbely.data.feed

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class FeedParserTest {
    private fun millis(iso: String) = ZonedDateTime.parse(iso).toInstant().toEpochMilli()

    @Test
    fun parsesRfc1123Dates() {
        assertEquals(millis("2024-03-05T14:30:00Z"), FeedParser.parseDate("Tue, 5 Mar 2024 14:30:00 GMT"))
        assertEquals(millis("2024-03-05T14:30:00+01:00"), FeedParser.parseDate("Tue, 05 Mar 2024 14:30:00 +0100"))
    }

    @Test
    fun parsesDatesWithZoneNamesAndExtraSpaces() {
        assertEquals(millis("2024-03-05T14:30:00Z"), FeedParser.parseDate("  Tue,  5 Mar 2024   14:30:00 UTC "))
    }

    @Test
    fun parsesDatesWithoutSecondsOrWeekday() {
        assertEquals(millis("2024-03-05T14:30:00Z"), FeedParser.parseDate("Tue, 5 Mar 2024 14:30 GMT"))
        assertEquals(millis("2024-03-05T14:30:00+01:00"), FeedParser.parseDate("5 Mar 2024 14:30:00 +0100"))
    }

    @Test
    fun parsesIsoDates() {
        assertEquals(millis("2024-03-05T14:30:00+02:00"), FeedParser.parseDate("2024-03-05T14:30:00+02:00"))
    }

    @Test
    fun unknownDateIsZero() {
        assertEquals(0L, FeedParser.parseDate("sometime last week"))
        assertEquals(0L, FeedParser.parseDate(""))
    }

    @Test
    fun parsesDurations() {
        assertEquals(3_723_000L, FeedParser.parseDuration("1:02:03"))
        assertEquals(3_723_000L, FeedParser.parseDuration("62:03"))
        assertEquals(3_723_000L, FeedParser.parseDuration("3723"))
        assertEquals(3_723_500L, FeedParser.parseDuration(" 3723.5 "))
    }

    @Test
    fun invalidDurationIsZero() {
        assertEquals(0L, FeedParser.parseDuration(""))
        assertEquals(0L, FeedParser.parseDuration("about an hour"))
    }
}
