package com.github.chsssssss.eonje.data.migration

import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.MIGRATING
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.NOT_NEEDED
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.WAITING
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyMigrationPhaseTest {
    private val uid = "uid-1"
    private val fresh = MigrationState()
    private val completed = MigrationState(uid = uid, completed = true)

    private fun phase(uid: String?, state: MigrationState, running: Boolean, roomHasData: Boolean) =
        runBlocking { legacyMigrationPhase(uid, state, running, { roomHasData }) }

    @Test
    fun 신규_설치는_Room이_비어_있어서_이전_대상이_없다() {
        // Worker가 아직 완료를 표시하기 전이어도, 로그인 전이어도, Worker가 돌고 있어도 영향이 없다.
        assertEquals(NOT_NEEDED, phase(uid, fresh, running = false, roomHasData = false))
        assertEquals(NOT_NEEDED, phase(uid, fresh, running = true, roomHasData = false))
        assertEquals(NOT_NEEDED, phase(null, fresh, running = false, roomHasData = false))
    }

    @Test
    fun 이전_전_Room에_데이터가_있고_아직_시작_못_했으면_대기다() {
        assertEquals(WAITING, phase(uid, fresh, running = false, roomHasData = true))
    }

    @Test
    fun 이전_중_Worker가_돌고_있으면_이전_중이다() {
        val partway = MigrationState(uid = uid, foldersDone = true, placeCursor = "p-5", placeCount = 5)
        assertEquals(MIGRATING, phase(uid, partway, running = true, roomHasData = true))
    }

    @Test
    fun 오프라인이면_Worker가_네트워크_조건을_기다리느라_돌지_않아서_대기다() {
        val partway = MigrationState(uid = uid, foldersDone = true)
        assertEquals(WAITING, phase(uid, partway, running = false, roomHasData = true))
    }

    @Test
    fun 로그인_전이면_Worker가_돌고_있어도_아직_못_옮기고_있으므로_대기다() {
        assertEquals(WAITING, phase(null, fresh, running = true, roomHasData = true))
        assertEquals(WAITING, phase(null, fresh, running = false, roomHasData = true))
    }

    @Test
    fun 이전이_끝나면_없음이고_Room은_읽지_않는다() {
        val outcome = runBlocking {
            legacyMigrationPhase(uid, completed, workerRunning = true, hasRoomData = { error("끝난 뒤엔 Room을 읽으면 안 된다") })
        }
        assertEquals(NOT_NEEDED, outcome)
    }

    @Test
    fun 완료_기록의_uid와_현재_uid가_다르면_다시_이전_대상이다() {
        // 자동 백업이 완료 기록을 복원했지만 새 기기에선 다른 uid로 로그인된 경우
        val restored = MigrationState(uid = "old-device-uid", completed = true)
        assertEquals(WAITING, phase(uid, restored, running = false, roomHasData = true))
        assertEquals(MIGRATING, phase(uid, restored, running = true, roomHasData = true))
    }
}
