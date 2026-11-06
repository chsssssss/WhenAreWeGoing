package com.github.chsssssss.eonje.data.migration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.MIGRATING
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.NOT_NEEDED
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase.WAITING
import com.github.chsssssss.eonje.domain.repository.UserSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 실제 DataStore와 Room(메모리)으로 [RoomMigrationStatusImpl]이 이전 전/중/후, 오프라인, 신규 설치를 구별하는지 확인한다.
 * Worker 실행 여부와 로그인만 가짜다. Firebase 에뮬레이터는 필요 없다.
 */
@RunWith(AndroidJUnit4::class)
class RoomMigrationStatusImplTest {
    private class Session(initialUid: String?) : UserSession {
        override val uid = MutableStateFlow(initialUid)
        override fun isSignedIn() = uid.value != null
        override fun currentUid() = uid.value
        override suspend fun signInIfNeeded() = isSignedIn()
    }

    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var db: EonjeDatabase
    private lateinit var store: RoomMigrationStateStore
    private val running = MutableStateFlow(false)

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = File(context.cacheDir, "status-test-${System.nanoTime()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, EonjeDatabase::class.java).allowMainThreadQueries().build()
        store = RoomMigrationStateStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "m.preferences_pb") }))
        running.value = false
    }

    @After
    fun tearDown() {
        db.close(); scope.cancel(); dir.deleteRecursively()
    }

    private fun status(session: Session) =
        RoomMigrationStatusImpl(store, session, db.folderDao(), db.placeDao(), db.savedPostDao(), running)

    private suspend fun RoomMigrationStatusImpl.await(expected: LegacyMigrationPhase) {
        withTimeout(5_000) { observePhase().first { it == expected } }
    }

    private suspend fun seedRoom() = db.folderDao().insert(FolderEntity("f1", "폴더", 1L, 0, "☕"))

    @Test
    fun 신규_설치는_Room이_비어_있어서_어떤_상황에서도_이전_대상이_아니다() = runBlocking {
        val impl = status(Session("uid-1"))
        impl.await(NOT_NEEDED)
        running.value = true
        impl.await(NOT_NEEDED)
        // 로그인 전이어도 마찬가지
        status(Session(null)).await(NOT_NEEDED)
    }

    @Test
    fun 이전_전_중_후_상태가_차례로_바뀐다() = runBlocking {
        seedRoom()
        val session = Session("uid-1")
        val impl = status(session)

        impl.await(WAITING) // 이전 전: 대상은 있는데 아직 시작 못 함
        running.value = true
        impl.await(MIGRATING) // 이전 중
        store.begin("uid-1", 1L)
        impl.await(MIGRATING)

        store.markCompleted("uid-1")
        impl.await(NOT_NEEDED) // 이전 후: 배너가 사라지고 정상 화면
        running.value = false
        impl.await(NOT_NEEDED)
    }

    @Test
    fun 오프라인이면_Worker가_돌지_않아_대기이고_연결되어_Worker가_돌면_이전_중이다() = runBlocking {
        seedRoom()
        val impl = status(Session("uid-1"))
        store.begin("uid-1", 1L) // 일부 진행하다 연결이 끊긴 상황
        store.saveProgress("uid-1", MigrationKind.FOLDERS, "f1", 1)

        running.value = false
        impl.await(WAITING)
        running.value = true
        impl.await(MIGRATING)
    }

    @Test
    fun 로그인_대기_중이면_대기이고_로그인되면_이전_중으로_바뀐다() = runBlocking {
        seedRoom()
        val session = Session(null)
        val impl = status(session)
        running.value = true

        impl.await(WAITING)
        session.uid.value = "uid-1"
        impl.await(MIGRATING)
    }

    @Test
    fun 다른_uid의_완료_기록은_다시_이전_대상으로_본다() = runBlocking {
        seedRoom()
        store.markCompleted("old-device-uid")
        val impl = status(Session("uid-1"))

        impl.await(WAITING)
        assertEquals(false, impl.isComplete())
        store.begin("uid-1", 1L); store.markCompleted("uid-1")
        impl.await(NOT_NEEDED)
        assertEquals(true, impl.isComplete())
    }

    @Test
    fun 건너뛴_게시물_ID는_현재_uid의_기록에서만_돌려준다() = runBlocking {
        seedRoom()
        store.begin("uid-1", 1L)
        store.saveProgress("uid-1", MigrationKind.POSTS, "p9", 3, listOf("POSTS:post-x:PERMISSION_DENIED", "PLACES:place-y:PERMISSION_DENIED"))
        store.markCompleted("uid-1")

        assertEquals(setOf("post-x"), status(Session("uid-1")).skippedPostIds())
        assertEquals("다른 uid의 기록은 쓰지 않는다", emptySet<String>(), status(Session("uid-2")).skippedPostIds())
        assertEquals("완료는 건너뛴 문서와 무관하다", true, status(Session("uid-1")).isComplete())
        status(Session("uid-1")).await(NOT_NEEDED)
    }
}
