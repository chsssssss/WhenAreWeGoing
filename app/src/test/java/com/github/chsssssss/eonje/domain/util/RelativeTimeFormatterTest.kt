package com.github.chsssssss.eonje.domain.util

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeTimeFormatterTest {

    private val now = 1_000_000_000L

    @Test
    fun `just now for under a minute`() {
        assertEquals("방금 전", RelativeTimeFormatter.format(now - 30_000, now))
    }

    @Test
    fun `minutes ago`() {
        assertEquals("5분 전", RelativeTimeFormatter.format(now - 5 * 60_000, now))
    }

    @Test
    fun `hours ago`() {
        assertEquals("3시간 전", RelativeTimeFormatter.format(now - 3 * 3_600_000L, now))
    }

    @Test
    fun `days ago`() {
        assertEquals("2일 전", RelativeTimeFormatter.format(now - 2 * 86_400_000L, now))
    }

    @Test
    fun `weeks ago`() {
        assertEquals("2주 전", RelativeTimeFormatter.format(now - 14 * 86_400_000L, now))
    }
}
