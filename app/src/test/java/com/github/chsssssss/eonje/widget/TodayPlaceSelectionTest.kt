package com.github.chsssssss.eonje.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class TodayPlaceSelectionTest {

    private val ids = listOf("a", "b", "c")

    @Test
    fun `no candidates gives empty state`() {
        assertNull(chooseTodayPick(emptyList(), "2026-10-31", stored = null))
    }

    @Test
    fun `same day keeps the stored pick`() {
        val stored = TodayPick("2026-10-31", "b", previousPlaceId = "a")
        repeat(20) { seed ->
            assertEquals(stored, chooseTodayPick(ids, "2026-10-31", stored, Random(seed)))
        }
    }

    @Test
    fun `new day never repeats yesterday when there is another choice`() {
        val stored = TodayPick("2026-10-30", "b", previousPlaceId = null)
        repeat(50) { seed ->
            val pick = chooseTodayPick(ids, "2026-10-31", stored, Random(seed))!!
            assertNotEquals("b", pick.placeId)
            assertEquals("b", pick.previousPlaceId)
            assertEquals("2026-10-31", pick.date)
        }
    }

    @Test
    fun `deleted pick is replaced the same day without bringing back yesterday`() {
        val stored = TodayPick("2026-10-31", "gone", previousPlaceId = "a")
        repeat(50) { seed ->
            val pick = chooseTodayPick(ids, "2026-10-31", stored, Random(seed))!!
            assertNotEquals("a", pick.placeId)
            assertEquals("a", pick.previousPlaceId)
        }
    }

    @Test
    fun `single candidate is shown even if it was yesterday's`() {
        val stored = TodayPick("2026-10-30", "a", previousPlaceId = null)
        assertEquals("a", chooseTodayPick(listOf("a"), "2026-10-31", stored)!!.placeId)
    }

    @Test
    fun `neighborhood prefers dong then gu then si`() {
        assertEquals("성수동2가", neighborhoodOf("서울 성동구 성수동2가 300-1"))
        assertEquals("후평동", neighborhoodOf("강원 춘천시 후평동 123"))
        assertEquals("성동구", neighborhoodOf("서울 성동구 성수이로7길 10"))
        assertEquals("분당구", neighborhoodOf("경기 성남시 분당구 판교역로 1"))
        assertEquals("춘천시", neighborhoodOf("강원특별자치도 춘천시 후평로 00"))
        assertNull(neighborhoodOf(null))
        assertNull(neighborhoodOf(""))
    }
}
