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

    // 아래는 실제 공유 링크(대구 맛집 계정 @fooddaegu, @daeguhotple)로 검증한 케이스.
    // 언더스코어(_)/하이픈(-)이 섞인 shortcode, 실제 stkn 트래킹 파라미터를 그대로 사용한다.

    @Test
    fun `extractShortcode handles real-world post link with underscore shortcode`() {
        val url = "https://www.instagram.com/p/DdEDSLbj_xa/?stkn=MXg3YzRjNWIxZmoycA=="
        assertEquals("DdEDSLbj_xa", InstagramUrlParser.extractShortcode(InstagramUrlParser.stripQueryParams(url)))
    }

    @Test
    fun `extractShortcode handles real-world reel link`() {
        val url = "https://www.instagram.com/reel/Da2G_vrkpLP/?stkn=N2JlYzJzdnE4MnAw"
        assertEquals("Da2G_vrkpLP", InstagramUrlParser.extractShortcode(InstagramUrlParser.stripQueryParams(url)))
    }

    @Test
    fun `extractShortcode handles real-world post link with hyphen shortcode`() {
        val url = "https://www.instagram.com/p/Dcr3D-lk6x3/?stkn=MXV3N3RzaHliaXN2YQ=="
        assertEquals("Dcr3D-lk6x3", InstagramUrlParser.extractShortcode(InstagramUrlParser.stripQueryParams(url)))
    }

    @Test
    fun `extractShortcode works even without stripping the tracking token first`() {
        // SaveSharedPostUseCase는 stripQueryParams를 먼저 호출하지만, 순서와 무관하게 안전해야 한다.
        val url = "https://www.instagram.com/reel/DajwvcrDEHI/?stkn=MTgzbWVtbHZncGkwMQ=="
        assertEquals("DajwvcrDEHI", InstagramUrlParser.extractShortcode(url))
    }

    @Test
    fun `same reel shared under two different accounts yields identical shortcode for dedup`() {
        // 실제로 @fooddaegu, @daeguhotple 두 계정 아래 똑같은 릴스 링크가 공유된 경우 —
        // shortcode만 같으면 SaveSharedPostUseCase가 중복으로 판단해 새 레코드를 만들지 않는다.
        val fromFooddaegu = "https://www.instagram.com/reel/DajwvcrDEHI/?stkn=MTgzbWVtbHZncGkwMQ=="
        val fromDaeguhotple = "https://www.instagram.com/reel/DajwvcrDEHI/?stkn=MTgzbWVtbHZncGkwMQ=="

        val shortcode1 = InstagramUrlParser.extractShortcode(InstagramUrlParser.stripQueryParams(fromFooddaegu))
        val shortcode2 = InstagramUrlParser.extractShortcode(InstagramUrlParser.stripQueryParams(fromDaeguhotple))

        assertEquals("DajwvcrDEHI", shortcode1)
        assertEquals(shortcode1, shortcode2)
    }
}
