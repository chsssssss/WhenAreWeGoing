package com.github.chsssssss.eonje.data.account

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.firestore.InMemoryPlaceSyncStore
import com.github.chsssssss.eonje.data.local.AndroidImageCodec
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRef
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.data.worker.ThumbnailMaintenanceWorker
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * [AccountLocalCleanupImpl] 실제 구현이 로컬 상태를 정말 지우는지 확인한다(계정 테스트에선 가짜로 대체했던 부분).
 * Room은 메모리 DB, 썸네일은 임시 폴더, WorkManager와 캐시 정리 플래그는 앱 것을 쓰고 테스트가 끝나면 되돌린다. Firebase는 필요 없다.
 */
@RunWith(AndroidJUnit4::class)
class AccountLocalCleanupImplTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: EonjeDatabase
    private lateinit var thumbDir: File
    private lateinit var placeSync: InMemoryPlaceSyncStore
    private lateinit var cleanup: AccountLocalCleanupImpl
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        FirestoreCacheClearFlag.clear(context)
        db = Room.inMemoryDatabaseBuilder(context, EonjeDatabase::class.java).allowMainThreadQueries().build()
        thumbDir = File(context.cacheDir, "cleanup-test-${System.nanoTime()}").apply { mkdirs() }
        placeSync = InMemoryPlaceSyncStore()
        workManager = WorkManager.getInstance(context)
        cleanup = AccountLocalCleanupImpl(context, ThumbnailStore(thumbDir, OkHttpClient(), AndroidImageCodec), placeSync, db, workManager)
    }

    @After
    fun tearDown() {
        db.close()
        thumbDir.deleteRecursively()
        FirestoreCacheClearFlag.clear(context) // 실제 앱의 다음 시작에 영향이 없게
        workManager.cancelUniqueWork(ThumbnailMaintenanceWorker.WORK_NAME)
    }

    private suspend fun seedRoom() {
        db.folderDao().insert(FolderEntity("f1", "폴더", 1L, 0xFF1E88E5.toInt(), "☕"))
        db.placeDao().insert(PlaceEntity("p1", "가게", "서울", 37.5, 127.0, "k1", "음식점", null, ResolveStatus.RESOLVED, 1L, 2L, "f1"))
        db.savedPostDao().insert(SavedPostEntity("s1", "SC1", "https://www.instagram.com/p/SC1/", "캡션", null, ResolveStatus.RESOLVED, 1, 1L, null))
        db.postPlaceCrossRefDao().insert(PostPlaceCrossRef("s1", "p1"))
        db.extractedCandidateDao().insertAll(listOf(ExtractedCandidateEntity("s1", 0, "후보", 0, "kc", "후보", "주소", null, 37.0, 127.0)))
    }

    @Test
    fun clearLegacyRoom은_모든_테이블의_데이터를_비우고_스키마는_그대로_둔다() = runBlocking {
        seedRoom()
        assertEquals(1, db.placeDao().count())

        cleanup.clearLegacyRoom()

        assertEquals(0, db.folderDao().count())
        assertEquals(0, db.placeDao().count())
        assertEquals(0, db.savedPostDao().count())
        assertTrue(db.postPlaceCrossRefDao().forPosts(listOf("s1")).isEmpty())
        assertTrue(db.extractedCandidateDao().forPosts(listOf("s1")).isEmpty())
        // 스키마는 그대로라서 다시 쓸 수 있다(테이블을 지운 게 아니다)
        seedRoom()
        assertEquals(1, db.placeDao().count())
    }

    @Test
    fun clearDeviceCaches는_썸네일_파일을_전부_지우고_장소_동기화_기록도_지운다() = runBlocking {
        File(thumbDir, "post-1.jpg").writeBytes(ByteArray(10) { 1 })
        File(thumbDir, "post-2.jpg").writeBytes(ByteArray(10) { 1 })
        File(thumbDir, "post-3.jpg.tmp").writeBytes(ByteArray(10) { 1 })
        placeSync.markFullSync("uid-1", 123L)

        cleanup.clearDeviceCaches()

        assertEquals("썸네일 폴더가 비었다", 0, thumbDir.listFiles()!!.size)
        assertNull("동기화 기록이 지워졌다", placeSync.lastFullSyncMillis("uid-1"))
    }

    @Test
    fun listThumbnailKeys는_jpg_썸네일의_게시물_ID만_돌려준다() = runBlocking {
        File(thumbDir, "post-1.jpg").writeBytes(ByteArray(10) { 1 })
        File(thumbDir, "post-2.jpg").writeBytes(ByteArray(10) { 1 })
        File(thumbDir, "post-3.jpg.tmp").writeBytes(ByteArray(10) { 1 })

        assertEquals(setOf("post-1", "post-2"), cleanup.listThumbnailKeys())
    }

    @Test
    fun clearThumbnails는_목록의_파일만_지우고_이후에_바뀐_파일과_보호_대상은_남기며_멱등이다() = runBlocking {
        val cutoff = 1_000_000L
        File(thumbDir, "old.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(cutoff - 1) }
        File(thumbDir, "old-same-time.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(cutoff) }
        File(thumbDir, "rewritten.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(cutoff + 1) } // 새 계정이 같은 게시물을 다시 저장해 받은 파일
        File(thumbDir, "protected.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(cutoff - 1) } // 지금 계정이 아는 게시물
        File(thumbDir, "not-listed.jpg").apply { writeBytes(ByteArray(10) { 1 }); setLastModified(cutoff - 1) } // 표시에 없는 파일

        val keys = setOf("old", "old-same-time", "rewritten", "protected", "already-gone")
        cleanup.clearThumbnails(keys, cutoff, protectedKeys = setOf("protected"))
        cleanup.clearThumbnails(keys, cutoff, protectedKeys = setOf("protected")) // 한 번 더 — 이미 지워진 상태에서도 안전

        assertEquals(setOf("rewritten.jpg", "protected.jpg", "not-listed.jpg"), thumbDir.list()!!.toSet())
    }

    @Test
    fun clearPlaceSyncFor는_그_uid의_기록만_지운다() = runBlocking {
        placeSync.markFullSync("old-uid", 1L)
        placeSync.markFullSync("new-uid", 2L)

        cleanup.clearPlaceSyncFor("old-uid")
        cleanup.clearPlaceSyncFor("old-uid") // 멱등

        assertNull(placeSync.lastFullSyncMillis("old-uid"))
        assertEquals(2L, placeSync.lastFullSyncMillis("new-uid"))
    }

    @Test
    fun requestFirestoreCacheClear는_다음_시작이_읽을_수_있게_플래그를_남긴다() {
        assertFalse(FirestoreCacheClearFlag.isRequested(context))

        cleanup.requestFirestoreCacheClear()

        assertTrue(FirestoreCacheClearFlag.isRequested(context))
    }

    @Test
    fun scheduleThumbnailMaintenance는_썸네일_정리_작업을_예약한다() {
        cleanup.scheduleThumbnailMaintenance()

        val infos = workManager.getWorkInfosForUniqueWork(ThumbnailMaintenanceWorker.WORK_NAME).get()
        assertTrue("작업이 예약됐다", infos.isNotEmpty())
        assertTrue(infos.none { it.state == WorkInfo.State.FAILED })
    }
}
