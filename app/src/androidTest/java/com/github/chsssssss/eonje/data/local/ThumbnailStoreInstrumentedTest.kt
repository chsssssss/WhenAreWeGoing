package com.github.chsssssss.eonje.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random

/** 새 게시물 저장 경로(다운로드 → 축소·재압축 → rename)를 실제 코덱으로 끝까지 확인한다. 네트워크는 가짜 응답. */
@RunWith(AndroidJUnit4::class)
class ThumbnailStoreInstrumentedTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        dir = File(cache, "store-test-${System.nanoTime()}")
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun 인스타_크기의_사진을_받아_800px_이하로_줄여_저장한다() = runBlocking {
        val original = jpegBytes(1080, 1350, quality = 95) // 인스타 대표 이미지 크기(4:5)
        val store = ThumbnailStore(dir, clientReturning(200, "image/jpeg", original), AndroidImageCodec)

        val result = store.cache("post-1", "https://cdn.example.test/p.jpg")

        val saved = File(dir, "post-1.jpg")
        assertEquals("file://${saved.absolutePath}", result)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(saved.path, it) }
        assertEquals(640, bounds.outWidth)
        assertEquals(800, bounds.outHeight)
        assertTrue("원본 ${original.size}B → ${saved.length()}B", saved.length() < original.size)
        assertEquals("최종 파일 외에 .tmp가 남으면 안 된다", listOf("post-1.jpg"), dir.list()!!.toList())
        println("썸네일 크기 변화: 1080x1350 ${original.size / 1024}KB → 640x800 ${saved.length() / 1024}KB")
    }

    @Test
    fun 오류_본문은_저장하지_않고_원격_URL로_폴백한다() = runBlocking {
        val store = ThumbnailStore(dir, clientReturning(403, "text/html", "<html>Forbidden</html>".toByteArray()), AndroidImageCodec)

        assertEquals("https://cdn.example.test/p.jpg", store.cache("post-2", "https://cdn.example.test/p.jpg"))
        assertTrue(dir.list().orEmpty().isEmpty())
    }

    private fun clientReturning(code: Int, contentType: String, body: ByteArray) = OkHttpClient.Builder()
        .addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(body.toResponseBody(contentType.toMediaType())).build()
        }
        .build()

    private fun jpegBytes(width: Int, height: Int, quality: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            Paint().apply { shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.RED, Color.BLUE, Shader.TileMode.CLAMP) },
        )
        val random = Random(3)
        val paint = Paint()
        repeat(150) {
            paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val x = random.nextInt(width).toFloat()
            val y = random.nextInt(height).toFloat()
            canvas.drawRect(x, y, x + width / 20f, y + height / 20f, paint)
        }
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray().also { bitmap.recycle() }
    }
}
