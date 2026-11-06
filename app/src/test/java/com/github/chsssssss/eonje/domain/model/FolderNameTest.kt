package com.github.chsssssss.eonje.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNameTest {
    @Test
    fun 입력_상한은_50자이고_규칙_상한_100자보다_작다() {
        assertEquals(50, FolderName.MAX_LENGTH)
        assertTrue(FolderName.MAX_LENGTH < 100)
    }

    @Test
    fun 상한_이하는_그대로_넘으면_50자로_자른다() {
        assertEquals("성수 맛집", FolderName.clamp("성수 맛집"))
        assertEquals("가".repeat(50), FolderName.clamp("가".repeat(50)))
        assertEquals("가".repeat(50), FolderName.clamp("가".repeat(120)))
    }

    @Test
    fun 붙여넣기로_들어온_긴_이모지_이름도_쌍을_깨뜨리지_않고_자른다() {
        val clamped = FolderName.clamp("가".repeat(49) + "😀😀😀")
        assertEquals("가".repeat(49), clamped)
    }
}
