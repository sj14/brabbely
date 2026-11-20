package io.github.sj14.brabbely.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeWindowTest {
    private fun at(hour: Int, minute: Int = 0) = hour * 60 + minute

    @Test
    fun windowWithinOneDay() {
        assertTrue(isInTimeWindow(at(14), start = at(13), end = at(15)))
        assertTrue(isInTimeWindow(at(13), start = at(13), end = at(15)))
        assertFalse(isInTimeWindow(at(15), start = at(13), end = at(15)))
        assertFalse(isInTimeWindow(at(12, 59), start = at(13), end = at(15)))
    }

    @Test
    fun windowAcrossMidnight() {
        assertTrue(isInTimeWindow(at(23), start = at(22), end = at(6)))
        assertTrue(isInTimeWindow(at(0), start = at(22), end = at(6)))
        assertTrue(isInTimeWindow(at(5, 59), start = at(22), end = at(6)))
        assertFalse(isInTimeWindow(at(6), start = at(22), end = at(6)))
        assertFalse(isInTimeWindow(at(12), start = at(22), end = at(6)))
    }
}
