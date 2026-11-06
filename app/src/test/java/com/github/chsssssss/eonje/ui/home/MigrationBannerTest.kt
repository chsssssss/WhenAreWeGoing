package com.github.chsssssss.eonje.ui.home

import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.MIGRATING
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.NOT_NEEDED
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.WAITING
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationBannerTest {
    private val delayMillis = 200L

    /** [phase]를 흘려 보내며 배너 방출을 모은다. [steps]는 (기다릴 시간, 바꿀 값) 순서. */
    private fun run(initial: LegacyMigrationPhase, vararg steps: Pair<Long, LegacyMigrationPhase>): List<MigrationBanner?> = runBlocking {
        val phase = MutableStateFlow(initial)
        val emitted = java.util.Collections.synchronizedList(mutableListOf<MigrationBanner?>())
        val job = launch(Dispatchers.Default) { migrationBannerFlow(phase, delayMillis).collect { emitted += it } }
        steps.forEach { (wait, next) -> delay(wait); phase.value = next }
        delay(120)
        job.cancel()
        emitted.toList()
    }

    @Test
    fun 이전_대상이_없으면_배너는_계속_없다() {
        assertEquals(listOf(null), run(NOT_NEEDED, 600L to NOT_NEEDED))
    }

    @Test
    fun 지연_시간_안에_끝나면_배너가_한_번도_안_보인다() {
        val emitted = run(MIGRATING, 80L to NOT_NEEDED, 300L to NOT_NEEDED)
        assertTrue("배너가 보였다: $emitted", emitted.none { it != null })
    }

    @Test
    fun 지연_시간_이상_이어지면_배너가_나타났다가_끝나면_사라진다() {
        val emitted = run(MIGRATING, 400L to NOT_NEEDED)
        assertEquals(listOf(null, MigrationBanner.MIGRATING, null), emitted)
    }

    @Test
    fun 대기가_이어지면_연결_안내_문구가_나온다() {
        val emitted = run(WAITING, 400L to WAITING)
        assertEquals(MigrationBanner.WAITING, emitted.last())
        assertTrue(MigrationBanner.WAITING.message.contains("인터넷에 연결되면"))
        assertTrue(MigrationBanner.WAITING.message.contains("데이터는 안전해요"))
    }

    @Test
    fun 배너가_뜬_뒤_대기에서_이전_중으로_바뀌면_지연_없이_문구만_바뀐다() {
        val emitted = run(WAITING, 400L to MIGRATING, 50L to MIGRATING)
        assertEquals(listOf(null, MigrationBanner.WAITING, MigrationBanner.MIGRATING), emitted)
    }

    @Test
    fun 지연_도중_이전_중과_대기가_오가도_처음_시점부터_센다() {
        // 80ms마다 상태가 바뀌어도 "이전이 필요한 상태"는 계속이므로 지연이 다시 시작되지 않는다.
        val emitted = run(WAITING, 60L to MIGRATING, 60L to WAITING, 60L to MIGRATING, 200L to MIGRATING)
        assertEquals(MigrationBanner.MIGRATING, emitted.last())
    }

    @Test
    fun 문구는_요청한_그대로다() {
        assertEquals("기존 데이터를 옮기는 중이에요", MigrationBanner.MIGRATING.message)
        assertEquals("인터넷에 연결되면 기존 데이터를 옮겨요. 데이터는 안전해요", MigrationBanner.WAITING.message)
    }

    // ── 화면 분기 ──

    @Test
    fun 빈_상태_카드는_이전_대상이_없을_때만_보인다() {
        assertTrue("신규 설치: 진짜 빈 상태", hasNoSavedPlaces(storedPlacesEmpty = true, phase = NOT_NEEDED))
        assertFalse("이전 전/대기", hasNoSavedPlaces(storedPlacesEmpty = true, phase = WAITING))
        assertFalse("이전 중", hasNoSavedPlaces(storedPlacesEmpty = true, phase = MIGRATING))
        assertFalse("장소가 있으면 빈 상태가 아니다", hasNoSavedPlaces(storedPlacesEmpty = false, phase = NOT_NEEDED))
    }

    @Test
    fun 목록_영역은_이전_전에도_중에도_저장된_장소가_없다는_말을_하지_않는다() {
        // 신규 설치(이전 없음): 안내 카드
        assertEquals(PlaceListContent.GUIDE_CARD, placeListContent(hasNoSavedPlaces = true, placesEmpty = true, migrationActive = false))
        // 이전 대상이 있는 사용자: 안내 카드도 "저장된 장소가 없어요"도 없이 비워둔다
        assertEquals(PlaceListContent.MIGRATION_PLACEHOLDER, placeListContent(hasNoSavedPlaces = false, placesEmpty = true, migrationActive = true))
        // 일부가 옮겨져 목록이 생기면 그대로 보여준다
        assertEquals(PlaceListContent.LIST, placeListContent(hasNoSavedPlaces = false, placesEmpty = false, migrationActive = true))
        // 이전이 끝난 뒤 필터·검색 결과가 없는 건 평소처럼
        assertEquals(PlaceListContent.NO_RESULTS, placeListContent(hasNoSavedPlaces = false, placesEmpty = true, migrationActive = false))
        assertEquals(PlaceListContent.LIST, placeListContent(hasNoSavedPlaces = false, placesEmpty = false, migrationActive = false))
    }
}
