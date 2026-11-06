package com.github.chsssssss.eonje.data.migration

import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacyDocSanitizerTest {
    private fun folder(name: String, icon: String = "☕") = Folder("f", name, 1L, 0xFF1E88E5.toInt(), icon)
    private fun place(name: String? = "이름", address: String? = "주소", category: String? = "카테고리") = Place(
        "p", name, address, 37.5, 127.0, "k-1", category, ResolveStatus.RESOLVED, 1L, 1L, "f",
    )
    private fun post(caption: String? = "캡션", url: String = "https://www.instagram.com/p/ABC123/") = SavedPost(
        "s", "ABC123", url, caption, null, ResolveStatus.PENDING, 0, 1L,
    )

    // ── 폴더 ──

    @Test
    fun 빈_이름과_공백뿐인_이름은_이름_없는_폴더가_된다() {
        for (blank in listOf("", " ", "   ", "\t\n")) {
            val result = LegacyDocSanitizer.folder(folder(blank))
            assertEquals("이름 없는 폴더", result.value.name)
            assertEquals(listOf("name"), result.fixedFields)
        }
    }

    @Test
    fun 상한_100자를_넘는_이름은_100자로_자르고_나머지_필드는_그대로다() {
        val result = LegacyDocSanitizer.folder(folder("가".repeat(101)))

        assertEquals("가".repeat(100), result.value.name)
        assertEquals(listOf("name"), result.fixedFields)
        assertEquals(0xFF1E88E5.toInt(), result.value.color)
        assertEquals("☕", result.value.iconKey)
        assertEquals("f", result.value.id)
        assertEquals(1L, result.value.createdAt)
    }

    @Test
    fun 상한_이하의_이름은_고치지_않고_같은_객체를_돌려준다() {
        for (name in listOf("성수 맛집", "가".repeat(100), "a")) {
            val input = folder(name)
            val result = LegacyDocSanitizer.folder(input)
            assertFalse(result.changed)
            assertSame(input, result.value)
        }
    }

    @Test
    fun 이모지_쌍_한가운데서_자르지_않는다() {
        // 😀는 UTF-16 두 단위. 100번째가 쌍의 앞 절반이면 쌍째로 뺀다 → 99자.
        val name = "가".repeat(99) + "😀" + "나".repeat(10)
        val result = LegacyDocSanitizer.folder(folder(name))

        assertEquals("가".repeat(99), result.value.name)
        assertTrue(result.value.name.length <= 100)
    }

    @Test
    fun 비정상적으로_긴_아이콘은_기본_아이콘으로_바꾼다() {
        val result = LegacyDocSanitizer.folder(folder("폴더", icon = "x".repeat(33)))
        assertEquals(FolderVisuals.DEFAULT_ICON, result.value.iconKey)
        assertEquals(listOf("iconKey"), result.fixedFields)
    }

    // ── 장소 ──

    @Test
    fun 장소의_이름_주소_카테고리는_각_상한까지_자른다() {
        val result = LegacyDocSanitizer.place(place("가".repeat(201), "나".repeat(301), "다".repeat(201)))

        assertEquals("가".repeat(200), result.value.name)
        assertEquals("나".repeat(300), result.value.address)
        assertEquals("다".repeat(200), result.value.category)
        assertEquals(listOf("name", "address", "category"), result.fixedFields)
    }

    @Test
    fun 장소는_넘는_필드만_고치고_식별자와_좌표는_건드리지_않는다() {
        val result = LegacyDocSanitizer.place(place(name = "가".repeat(201)))

        assertEquals(listOf("name"), result.fixedFields)
        assertEquals("k-1", result.value.kakaoPlaceId)
        assertEquals(37.5, result.value.latitude!!, 0.0)
        assertEquals("f", result.value.folderId)
    }

    @Test
    fun 장소의_null_필드는_그대로_두고_상한_이하면_고치지_않는다() {
        val withNulls = place(name = null, address = null, category = null)
        assertFalse(LegacyDocSanitizer.place(withNulls).changed)
        assertFalse(LegacyDocSanitizer.place(place("가".repeat(200), "나".repeat(300), "다".repeat(200))).changed)
    }

    @Test
    fun 식별자가_긴_장소와_좌표가_범위_밖인_장소는_고치지_않는다() {
        val longId = place().copy(kakaoPlaceId = "k".repeat(101))
        val badLat = place().copy(latitude = 91.0)

        assertFalse(LegacyDocSanitizer.place(longId).changed)
        assertEquals("k".repeat(101), LegacyDocSanitizer.place(longId).value.kakaoPlaceId)
        assertFalse(LegacyDocSanitizer.place(badLat).changed)
        assertEquals(91.0, LegacyDocSanitizer.place(badLat).value.latitude!!, 0.0)
    }

    // ── 게시물 ──

    @Test
    fun 캡션은_20000자까지_자른다() {
        val result = LegacyDocSanitizer.post(post(caption = "가".repeat(20_001)))

        assertEquals(20_000, result.value.caption!!.length)
        assertEquals(listOf("caption"), result.fixedFields)
        assertFalse(LegacyDocSanitizer.post(post(caption = "가".repeat(20_000))).changed)
        assertFalse(LegacyDocSanitizer.post(post(caption = null)).changed)
    }

    @Test
    fun 너무_긴_인스타_URL은_정규형으로_바꾼다() {
        val result = LegacyDocSanitizer.post(post(url = "https://m.instagram.com/reel/XyZ_9-1/?igsh=" + "q".repeat(600)))

        assertEquals("https://www.instagram.com/reel/XyZ_9-1/", result.value.instagramUrl)
        assertEquals(listOf("instagramUrl"), result.fixedFields)
    }

    @Test
    fun 해석할_수_없거나_정규형도_500자를_넘는_URL은_고치지_않는다() {
        val notInstagram = "https://example.com/" + "a".repeat(600)
        val hugeCode = "https://www.instagram.com/p/" + "a".repeat(500)

        assertFalse(LegacyDocSanitizer.post(post(url = notInstagram)).changed)
        assertEquals(notInstagram, LegacyDocSanitizer.post(post(url = notInstagram)).value.instagramUrl)
        assertFalse(LegacyDocSanitizer.post(post(url = hugeCode)).changed)
    }

    @Test
    fun 게시물의_식별자와_상태는_건드리지_않는다() {
        val input = post(caption = "가".repeat(21_000)).copy(shortcode = "s".repeat(101), extractedCount = 5_000)
        val result = LegacyDocSanitizer.post(input)

        assertEquals(listOf("caption"), result.fixedFields)
        assertEquals("s".repeat(101), result.value.shortcode)
        assertEquals(5_000, result.value.extractedCount)
        assertEquals(ResolveStatus.PENDING, result.value.status)
    }
}
