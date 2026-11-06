package com.github.chsssssss.eonje.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.repository.RoomSavedPostRepository
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * "업데이트 전에 쌓인 파일이 있는 상태"를 흉내 낸다: 실제 코덱, 실제 Room(인메모리), 실제 SQL로
 * 큰 파일 / 이미 작은 파일 / 깨진 파일 / 고아 파일이 섞인 폴더를 정리하고 결과를 확인한다.
 */
@RunWith(AndroidJUnit4::class)
class ThumbnailMaintenanceInstrumentedTest {

    private lateinit var dir: File
    private lateinit var database: EonjeDatabase
    private lateinit var maintenance: ThumbnailMaintenance
    private lateinit var state: MemoryState
    private lateinit var repository: RoomSavedPostRepository

    private class MemoryState : ThumbnailMaintenanceState {
        var done = false
        override suspend fun isCompactionDone() = done
        override suspend fun markCompactionDone() {
            done = true
        }
    }

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        dir = File(context.cacheDir, "maintenance-test-${System.nanoTime()}").apply { mkdirs() }
        database = Room.inMemoryDatabaseBuilder(context, EonjeDatabase::class.java).allowMainThreadQueries().build()
        val store = ThumbnailStore(dir, OkHttpClient(), AndroidImageCodec)
        repository = RoomSavedPostRepository(database.savedPostDao(), store)
        state = MemoryState()
        maintenance = ThumbnailMaintenance(store, repository, state, object : LegacyMigrationStatus {
            override suspend fun isComplete() = true
            override suspend fun isLegacyDuplicate(shortcode: String) = false
        })
    }

    @After
    fun tearDown() {
        database.close()
        dir.deleteRecursively()
    }

    @Test
    fun 섞여_있는_폴더를_정리하고_이미지는_계속_열린다() = runBlocking {
        val big = writeJpeg("big.jpg", 1080, 1350, quality = 95)
        val small = writeJpeg("small.jpg", 400, 500, quality = 70)
        val smallBytes = small.readBytes()
        File(dir, "broken.jpg").writeText("<html>403 Forbidden</html>")
        File(dir, "orphan.jpg").writeBytes(ByteArray(1000))
        insertPost("big", localThumbnail = "big")
        insertPost("small", localThumbnail = "small")
        insertPost("broken", localThumbnail = "broken")
        insertPost("remote", thumbnailUrl = "https://cdn.example.test/expired.jpg") // 원격 URL 폴백이 저장된 게시물
        val bigBefore = big.length()

        val report = maintenance.run(nowMillis = System.currentTimeMillis())

        // 큰 파일: 긴 변 800px 이하 JPEG으로 줄었고 여전히 이미지로 열린다
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(big.path, it) }
        assertEquals(640, bounds.outWidth)
        assertEquals(800, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertNotNull("전체 디코딩도 돼야 한다", BitmapFactory.decodeFile(big.path))
        assertTrue("용량이 줄어야 한다: $bigBefore → ${big.length()}", big.length() < bigBefore)
        // 이미 작은 파일: 바이트까지 그대로
        assertEquals(smallBytes.toList(), small.readBytes().toList())
        // 깨진 파일은 지워지고, 그 게시물의 로컬 경로만 비워진다. 원격 URL 게시물은 그대로.
        assertFalse(File(dir, "broken.jpg").exists())
        assertNull(repository.findById("broken")!!.thumbnailUrl)
        assertEquals("https://cdn.example.test/expired.jpg", repository.findById("remote")!!.thumbnailUrl)
        assertEquals("file://${big.absolutePath}", repository.findById("big")!!.thumbnailUrl)
        // 고아 파일은 지워지고 게시물 행은 하나도 사라지지 않는다
        assertFalse(File(dir, "orphan.jpg").exists())
        assertEquals(4, repository.observeAll().first().size)
        assertEquals(1, report.orphansDeleted)
        assertEquals(2, report.compaction!!.processedFiles) // big 줄임 + broken 삭제
        assertTrue(report.compaction!!.savedBytes > 0)
        assertEquals(listOf("big.jpg", "small.jpg"), dir.list()!!.sorted())
        assertTrue(state.done)
    }

    @Test
    fun 두_번째_실행은_1회_정리를_건너뛰고_고아만_정리한다() = runBlocking {
        writeJpeg("big.jpg", 1080, 1350, quality = 95)
        insertPost("big", localThumbnail = "big")
        maintenance.run(System.currentTimeMillis())
        val sizeAfterFirst = File(dir, "big.jpg").length()
        File(dir, "orphan.jpg").writeBytes(byteArrayOf(1, 2, 3))

        val second = maintenance.run(System.currentTimeMillis())

        assertNull(second.compaction)
        assertEquals(1, second.orphansDeleted)
        assertEquals("한 번 줄인 파일은 다시 건드리지 않는다", sizeAfterFirst, File(dir, "big.jpg").length())
    }

    @Test
    fun clearLocalThumbnail은_file_경로만_비운다() = runBlocking {
        insertPost("local", localThumbnail = "local")
        insertPost("remote", thumbnailUrl = "https://cdn.example.test/a.jpg")

        repository.clearLocalThumbnail("local")
        repository.clearLocalThumbnail("remote")

        assertNull(repository.findById("local")!!.thumbnailUrl)
        assertEquals("https://cdn.example.test/a.jpg", repository.findById("remote")!!.thumbnailUrl)
    }

    // ── 도구 ──

    private suspend fun insertPost(id: String, localThumbnail: String? = null, thumbnailUrl: String? = null) {
        database.savedPostDao().insert(
            SavedPostEntity(
                id = id,
                shortcode = id,
                instagramUrl = "https://www.instagram.com/p/$id/",
                caption = null,
                thumbnailUrl = localThumbnail?.let { "file://${File(dir, "$it.jpg").absolutePath}" } ?: thumbnailUrl,
                status = ResolveStatus.RESOLVED,
                extractedCount = 0,
                createdAt = 0L,
            ),
        )
    }

    private fun writeJpeg(name: String, width: Int, height: Int, quality: Int): File {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.RED, Color.BLUE, Shader.TileMode.CLAMP) },
        )
        val random = Random(7)
        val rectPaint = Paint()
        repeat(150) {
            rectPaint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val x = random.nextInt(width).toFloat()
            val y = random.nextInt(height).toFloat()
            canvas.drawRect(x, y, x + width / 20f, y + height / 20f, rectPaint)
        }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
        return file
    }
}
