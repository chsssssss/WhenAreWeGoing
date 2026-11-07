package com.github.chsssssss.eonje.data.firestore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceSyncPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test
    fun 기록이_없으면_전체를_받는다() {
        assertTrue(PlaceSyncPolicy.needsFullSync(null, cacheHasPlaces = true, nowMillis = now))
    }

    @Test
    fun 캐시가_비었으면_기록이_있어도_전체를_받는다() {
        assertTrue(PlaceSyncPolicy.needsFullSync(now - day, cacheHasPlaces = false, nowMillis = now))
    }

    @Test
    fun 묘비_보관_기간보다_짧은_여유_안이면_델타만_받는다() {
        assertFalse(PlaceSyncPolicy.needsFullSync(now - 79 * day, cacheHasPlaces = true, nowMillis = now))
        assertTrue(PlaceSyncPolicy.needsFullSync(now - 81 * day, cacheHasPlaces = true, nowMillis = now))
    }

    @Test
    fun 전체_동기화_한도는_묘비_보관_기간보다_짧다() {
        assertTrue(PlaceSyncPolicy.FULL_SYNC_MAX_AGE_MILLIS < TOMBSTONE_RETENTION_MILLIS)
    }

    @Test
    fun 시계가_거꾸로_가면_전체를_받는다() {
        assertTrue(PlaceSyncPolicy.needsFullSync(now + day, cacheHasPlaces = true, nowMillis = now))
    }

    @Test
    fun 델타_기준은_캐시의_최신_updatedAt에서_여유를_뺀_값이고_없으면_마지막_동기화_시각이다() {
        assertEquals(1_000_000L - PlaceSyncPolicy.CURSOR_MARGIN_MILLIS, PlaceSyncPolicy.deltaCursorMillis(1_000_000L, 5_000_000L))
        assertEquals(5_000_000L - PlaceSyncPolicy.CURSOR_MARGIN_MILLIS, PlaceSyncPolicy.deltaCursorMillis(null, 5_000_000L))
    }
}
