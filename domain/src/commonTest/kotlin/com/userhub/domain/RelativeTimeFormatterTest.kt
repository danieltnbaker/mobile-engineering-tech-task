package com.userhub.domain

import com.userhub.domain.time.formatRelativeTime
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeFormatterTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `returns just now for timestamps under a minute`() {
        assertEquals("Just now", formatRelativeTime(now - 30_000, now))
    }

    @Test
    fun `returns singular minute for exactly one minute`() {
        assertEquals("1 minute ago", formatRelativeTime(now - 60_000, now))
    }

    @Test
    fun `returns plural minutes for five minutes`() {
        assertEquals("5 minutes ago", formatRelativeTime(now - 5 * 60_000, now))
    }

    @Test
    fun `returns plural minutes close to the hour boundary`() {
        assertEquals("59 minutes ago", formatRelativeTime(now - 59 * 60_000, now))
    }

    // The following cross the hour and day boundaries. Before P0-3 the Android and iOS actual
    // implementations diverged here (e.g. 90 min -> "1 hour ago" vs "90 minutes ago"; 25 h ->
    // "1 day ago" vs "Yesterday"), and the suite never exercised these inputs. With a single shared
    // implementation the output is defined and identical on both platforms.

    private fun minutesAgo(m: Long) = formatRelativeTime(now - m * 60_000, now)

    @Test
    fun `collapses to one hour from sixty minutes up to two hours`() {
        assertEquals("1 hour ago", minutesAgo(60))
        assertEquals("1 hour ago", minutesAgo(90))
        assertEquals("1 hour ago", minutesAgo(119))
    }

    @Test
    fun `reports whole hours from two hours up to a day`() {
        assertEquals("2 hours ago", minutesAgo(120))
        assertEquals("23 hours ago", minutesAgo(23 * 60))
    }

    @Test
    fun `reports one day across the first day boundary`() {
        assertEquals("1 day ago", minutesAgo(24 * 60))
        assertEquals("1 day ago", minutesAgo(25 * 60))
        assertEquals("1 day ago", minutesAgo(2 * 1_440 - 1))
    }

    @Test
    fun `reports whole days beyond two days`() {
        assertEquals("2 days ago", minutesAgo(2 * 1_440))
        assertEquals("5 days ago", minutesAgo(5 * 1_440))
    }
}
