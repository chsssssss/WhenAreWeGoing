package com.github.chsssssss.eonje.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InstagramUrlParserTest {

    @Test
    fun `findUrl extracts url from surrounding text`() {
        val text = "https://www.instagram.com/p/ABC123/?igsh=xyz"
        assertEquals("https://www.instagram.com/p/ABC123/?igsh=xyz", InstagramUrlParser.findUrl(text))
    }

    @Test
    fun `findUrl returns null when no url present`() {
        assertNull(InstagramUrlParser.findUrl("그냥 텍스트"))
    }

    @Test
    fun `stripQueryParams removes query string`() {
        val stripped = InstagramUrlParser.stripQueryParams("https://www.instagram.com/reel/ABC123/?igsh=xyz")
        assertEquals("https://www.instagram.com/reel/ABC123/", stripped)
    }

    @Test
    fun `stripQueryParams leaves url without query untouched`() {
        val url = "https://www.instagram.com/p/ABC123/"
        assertEquals(url, InstagramUrlParser.stripQueryParams(url))
    }

    @Test
    fun `extractShortcode handles post reel and tv paths`() {
        assertEquals("ABC123", InstagramUrlParser.extractShortcode("https://www.instagram.com/p/ABC123/"))
        assertEquals("DEF456", InstagramUrlParser.extractShortcode("https://www.instagram.com/reel/DEF456/"))
        assertEquals("GHI789", InstagramUrlParser.extractShortcode("https://www.instagram.com/tv/GHI789/"))
    }

    @Test
    fun `extractShortcode returns null for unrelated url`() {
        assertNull(InstagramUrlParser.extractShortcode("https://www.instagram.com/someuser/"))
    }
}
