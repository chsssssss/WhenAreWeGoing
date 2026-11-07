package com.github.chsssssss.eonje.data.account

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.firestore.InMemoryPlaceSyncStore
import com.github.chsssssss.eonje.data.local.AndroidImageCodec
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 계정 삭제 뒤 "정리 대기" 표시와 다음 시작의 재시도를 확인한다. Firebase는 필요 없다(DataStore는 임시 파일, Room은 메모리 DB).
 * "앱 재시작"은 같은 DataStore 파일을 쓰는 새 [PendingLocalCleanup]을 만들어서 흉내낸다(같은 파일에 DataStore 인스턴스를 둘 만들 수 없어서
 * 저장소 객체는 그대로 쓴다).
 */
@RunWith(AndroidJUnit4::class)
class PendingLocalCleanupTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var store: PendingLocalCleanupStore
    private lateinit var events: MutableList<Boolean>
    private var now = 10_000L

    @Before
    fun setUp() {
        FirestoreCacheClearFlag.clear(context)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = File(context.cacheDir, "pending-cleanup-test-${System.nanoTime()}").apply { mkdirs() }
        store = PendingLocalCleanupStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "p.preferences_pb") }))
        events = mutableListOf()
    }

    @After
    fun tearDown() {
        scope.cancel()
        dir.deleteRecursively()
        FirestoreCacheClearFlag.clear(context) // 실제 앱의 다음 시작에 영향이 없게
    }

    private fun runner(cleanup: AccountLocalCleanup, protectedKeys: Set<String> = emptySet()) =
        PendingLocalCleanup(store, cleanup, { protectedKeys }, { events += it }, nowMillis = { now })

    /** 호출을 기록하고, [failRoom]이면 Room 단계에서 실패하는 가짜 정리. */
    private class FakeCleanup(var failRoom: Boolean = false, var keys: Set<String> = setOf("a", "b")) : AccountLocalCleanup {
        val calls = mutableListOf<String>()
        override suspend fun clearDeviceCaches() { calls += "device" }
        override suspend fun listThumbnailKeys(): Set<String> = keys
        override suspend fun clearThumbnails(keys: Set<String>, olderThanMillis: Long, protectedKeys: Set<String>) { calls += "thumbs:${keys.sorted()}" }
        override suspend fun clearPlaceSyncFor(uid: String) { calls += "sync:$uid" }
        override suspend fun clearLegacyRoom() { calls += "room"; if (failRoom) throw IllegalStateException("실패") }
        override fun requestFirestoreCacheClear() { calls += "cache" }
        override fun scheduleThumbnailMaintenance() { calls += "maintenance" }
    }

    // ── 표시 → 정리 → 지움 ──

    @Test
    fun 정리가_모두_성공하면_표시가_지워진다() = runBlocking {
        val cleanup = FakeCleanup()
        val runner = runner(cleanup)

        val pending = runner.begin("old-uid")
        assertNotNull("정리 전에 표시가 있다", store.read())
        val ok = runner.finish(pending)

        assertTrue(ok)
        assertNull(store.read())
        assertEquals(listOf("thumbs:[a, b]", "sync:old-uid", "room", "cache"), cleanup.calls)
    }

    @Test
    fun 정리_도중_한_단계가_실패하면_나머지는_계속하고_표시가_남는다() = runBlocking {
        val cleanup = FakeCleanup(failRoom = true)
        val runner = runner(cleanup)

        val ok = runner.finish(runner.begin("old-uid"))

        assertEquals(false, ok)
        assertNotNull("실패하면 표시가 남는다", store.read())
        assertEquals("실패한 단계 뒤의 단계도 실행된다", listOf("thumbs:[a, b]", "sync:old-uid", "room", "cache"), cleanup.calls)
    }

    @Test
    fun 다음_시작에_재시도하면_끝나고_표시가_지워지고_결과_이벤트가_남는다() = runBlocking {
        val failing = FakeCleanup(failRoom = true)
        runner(failing).finish(runner(failing).begin("old-uid")) // 첫 시도 실패 → 표시 남음
        assertNotNull(store.read())

        val recovered = FakeCleanup(failRoom = false) // 다음 시작(새 인스턴스)
        runner(recovered).retryIfPending()

        assertNull(store.read())
        assertEquals(listOf("thumbs:[a, b]", "sync:old-uid", "room", "cache"), recovered.calls)
        assertEquals("재시도 결과(성공)만 기록", listOf(true), events)
    }

    @Test
    fun 정리를_시작도_못_하고_앱이_죽어_표시만_남아도_다음_시작에_정리한다() = runBlocking {
        val cleanup = FakeCleanup()
        runner(cleanup).begin("old-uid") // 표시만 남기고 finish 전에 프로세스가 죽었다
        assertEquals(emptyList<String>(), cleanup.calls)

        runner(cleanup).retryIfPending() // 다음 시작

        assertEquals(listOf("thumbs:[a, b]", "sync:old-uid", "room", "cache"), cleanup.calls)
        assertNull(store.read())
    }

    @Test
    fun 재시도가_반복해서_실패해도_예외를_던지지_않고_표시를_남기며_실패_결과만_기록한다() = runBlocking {
        val cleanup = FakeCleanup(failRoom = true)
        runner(cleanup).begin("old-uid")

        runner(cleanup).retryIfPending()
        runner(cleanup).retryIfPending()

        assertNotNull(store.read())
        assertEquals(listOf(false, false), events)
    }

    @Test
    fun 표시가_없으면_아무것도_하지_않는다() = runBlocking {
        val cleanup = FakeCleanup()

        runner(cleanup).retryIfPending()

        assertEquals("아무 단계도 실행되지 않는다", emptyList<String>(), cleanup.calls)
        assertEquals("재시도 이벤트도 없다", emptyList<Boolean>(), events)
        assertNull(store.read())
    }

    @Test
    fun 표시를_다시_남기면_이름은_합치고_시각은_더_나중_것을_쓴다() = runBlocking {
        store.markPending("old-uid", setOf("a", "b"), 100L)

        val merged = store.markPending("old-uid", setOf("b", "c"), 50L) // 시각이 더 이르면 이른 쪽을 쓰지 않는다

        assertEquals(setOf("a", "b", "c"), merged.thumbnailKeys)
        assertEquals(100L, merged.markedAtMillis)
        assertEquals(merged, store.read())
    }

    // ── 실제 정리 구현으로: 새 계정의 썸네일 보존, 표시 없을 때 무삭제, 멱등 ──

    private class Real(context: Context, thumbDir: File, scope: CoroutineScope) {
        val db: EonjeDatabase = Room.inMemoryDatabaseBuilder(context, EonjeDatabase::class.java).allowMainThreadQueries().build()
        val placeSync = InMemoryPlaceSyncStore()
        val cleanup = AccountLocalCleanupImpl(context, ThumbnailStore(thumbDir, OkHttpClient(), AndroidImageCodec), placeSync, db, WorkManager.getInstance(context))
        suspend fun seedRoom() {
            db.folderDao().insert(FolderEntity("f1", "폴더", 1L, 0xFF1E88E5.toInt(), "☕"))
            db.placeDao().insert(PlaceEntity("p1", "가게", "서울", 37.5, 127.0, "k1", "음식점", null, ResolveStatus.RESOLVED, 1L, 2L, "f1"))
        }
    }

    @Test
    fun 재시도_전에_새_계정이_저장한_썸네일은_지우지_않는다() = runBlocking {
        val thumbDir = File(dir, "thumbs").apply { mkdirs() }
        val real = Real(context, thumbDir, scope)
        try {
            // 삭제한 계정의 썸네일 3개(표시 시각보다 오래된 파일)
            listOf("old-1", "old-2", "old-3").forEach { File(thumbDir, "$it.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(now - 5_000) } }
            real.placeSync.markFullSync("old-uid", 1L)
            val first = runner(real.cleanup)
            first.begin("old-uid") // 표시: old-1, old-2, old-3 / 시각 = now. 그리고 앱이 죽는다 — 정리는 아직 안 했다.

            // 그 사이 새 익명 계정이 쓰기 시작한다
            now += 60_000
            File(thumbDir, "new-1.jpg").apply { writeBytes(ByteArray(10) { 2 }); setLastModified(now) } // 새 게시물(표시에 없는 이름)
            File(thumbDir, "old-2.jpg").apply { writeBytes(ByteArray(10) { 3 }); setLastModified(now) } // 같은 인스타 게시물을 다시 저장해 새로 받은 파일(결정적 ID라 같은 이름)
            real.placeSync.markFullSync("new-uid", 2L)

            // 다음 시작: 새 계정은 old-3 게시물도 이미 알고 있다(이미 있던 파일을 그대로 쓴다)
            runner(real.cleanup, protectedKeys = setOf("old-3")).retryIfPending()

            assertEquals("old-1만 지워지고 나머지는 남는다", setOf("new-1.jpg", "old-2.jpg", "old-3.jpg"), thumbDir.list()!!.toSet())
            assertEquals("새 계정이 다시 받은 파일 내용이 그대로", 3, File(thumbDir, "old-2.jpg").readBytes()[0].toInt())
            assertNull("삭제한 계정의 동기화 기록은 지워지고", real.placeSync.lastFullSyncMillis("old-uid"))
            assertEquals("새 계정의 기록은 남는다", 2L, real.placeSync.lastFullSyncMillis("new-uid"))
            assertNull(store.read())
        } finally {
            real.db.close()
        }
    }

    @Test
    fun 표시가_없으면_실제_정리_구현도_썸네일_Room_동기화_기록을_그대로_둔다() = runBlocking {
        val thumbDir = File(dir, "thumbs").apply { mkdirs() }
        val real = Real(context, thumbDir, scope)
        try {
            File(thumbDir, "keep.jpg").writeBytes(ByteArray(10) { 1 })
            real.seedRoom()
            real.placeSync.markFullSync("uid", 1L)

            runner(real.cleanup).retryIfPending()

            assertEquals(listOf("keep.jpg"), thumbDir.list()!!.toList())
            assertEquals(1, real.db.placeDao().count())
            assertEquals(1L, real.placeSync.lastFullSyncMillis("uid"))
            assertTrue("캐시 정리 플래그도 세우지 않는다", !FirestoreCacheClearFlag.isRequested(context))
        } finally {
            real.db.close()
        }
    }

    @Test
    fun 실제_정리는_멱등이다_이미_지워진_상태에서_다시_실행해도_안전하다() = runBlocking {
        val thumbDir = File(dir, "thumbs").apply { mkdirs() }
        val real = Real(context, thumbDir, scope)
        try {
            File(thumbDir, "gone.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(now - 1_000) }
            real.seedRoom()
            val pending = runner(real.cleanup).begin("old-uid")

            assertTrue(runner(real.cleanup).finish(pending))
            // 표시가 지워졌어도 같은 내용으로 한 번 더 실행해도 아무 문제 없다(이미 없는 파일·빈 Room)
            assertTrue(runner(real.cleanup).finish(pending))

            assertEquals(0, thumbDir.listFiles()!!.size)
            assertEquals(0, real.db.placeDao().count())
            real.seedRoom() // 스키마는 그대로
            assertEquals(1, real.db.placeDao().count())
        } finally {
            real.db.close()
            FirestoreCacheClearFlag.clear(context)
        }
    }
}
