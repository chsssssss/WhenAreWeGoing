package com.github.chsssssss.eonje.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** 실제 BitmapFactory/ExifInterface로 리사이즈·재압축·EXIF 회전 결과의 크기를 확인한다. */
@RunWith(AndroidJUnit4::class)
class ThumbnailImageCodecTest {

    private lateinit var dir: File

    @Before
    fun setUp() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        dir = File(cache, "codec-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun 큰_이미지는_긴_변_800px_JPEG으로_줄고_용량도_줄어든다() {
        val src = writeJpeg("big.jpg", width = 2400, height = 3000, quality = 95)
        val dest = File(dir, "out.jpg")

        val result = AndroidImageCodec.downscale(src, dest, maxLongSide = 800, quality = 80)

        assertEquals(DownscaleResult.Done(640, 800), result) // 4:5 비율 유지
        val bounds = boundsOf(dest)
        assertEquals(640, bounds.outWidth)
        assertEquals(800, bounds.outHeight)
        assertEquals("image/jpeg", bounds.outMimeType)
        assertTrue("줄인 파일(${dest.length()}B)이 원본(${src.length()}B)보다 작아야 한다", dest.length() < src.length())
    }

    @Test
    fun 샘플링이_걸리는_아주_큰_이미지도_정확히_800px로_맞춘다() {
        // 3200x4000: inSampleSize 4로 800x1000까지 디코딩한 뒤 640x800으로 정확히 줄인다.
        val src = writeJpeg("huge.jpg", width = 3200, height = 4000, quality = 90, config = Bitmap.Config.RGB_565)
        val dest = File(dir, "out.jpg")

        val result = AndroidImageCodec.downscale(src, dest, maxLongSide = 800, quality = 80)

        assertEquals(DownscaleResult.Done(640, 800), result)
        assertEquals(800, boundsOf(dest).outHeight)
    }

    @Test
    fun 작은_이미지는_키우지_않는다() {
        val src = writeJpeg("small.jpg", width = 400, height = 300, quality = 90)
        val dest = File(dir, "out.jpg")

        val result = AndroidImageCodec.downscale(src, dest, maxLongSide = 800, quality = 80)

        assertEquals(DownscaleResult.Done(400, 300), result)
    }

    @Test
    fun EXIF_회전_정보는_픽셀에_반영된다() {
        // 가로 1600x800 사진에 "시계 방향 90도" 태그 → 세로 이미지로 보여야 하고, 줄이면 400x800.
        val src = writeJpeg("rotated.jpg", width = 1600, height = 800, quality = 90)
        ExifInterface(src.path).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val dest = File(dir, "out.jpg")

        val result = AndroidImageCodec.downscale(src, dest, maxLongSide = 800, quality = 80)

        assertEquals(DownscaleResult.Done(400, 800), result)
        val bounds = boundsOf(dest)
        assertTrue("세로 이미지여야 한다: ${bounds.outWidth}x${bounds.outHeight}", bounds.outHeight > bounds.outWidth)
    }

    @Test
    fun 투명_PNG는_검은_배경이_아니라_흰_배경이_된다() {
        val src = File(dir, "alpha.png")
        val transparent = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888) // 전부 투명
        src.outputStream().use { transparent.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val dest = File(dir, "out.jpg")

        val result = AndroidImageCodec.downscale(src, dest, maxLongSide = 800, quality = 80)

        assertTrue(result is DownscaleResult.Done)
        val decoded = BitmapFactory.decodeFile(dest.path)
        val pixel = decoded.getPixel(150, 150)
        assertTrue(
            "흰색에 가까워야 한다: #${Integer.toHexString(pixel)}",
            Color.red(pixel) > 240 && Color.green(pixel) > 240 && Color.blue(pixel) > 240,
        )
    }

    @Test
    fun 이미지가_아닌_파일은_Undecodable이고_결과_파일을_만들지_않는다() {
        val src = File(dir, "error.jpg").apply { writeText("<html>403 Forbidden</html>") }
        val dest = File(dir, "out.jpg")

        assertNull(AndroidImageCodec.probe(src))
        assertEquals(DownscaleResult.Undecodable, AndroidImageCodec.downscale(src, dest, maxLongSide = 800, quality = 80))
        assertFalse(dest.exists())
    }

    @Test
    fun 빈_파일도_Undecodable이다() {
        val src = File(dir, "empty.jpg").apply { createNewFile() }

        assertNull(AndroidImageCodec.probe(src))
    }

    @Test
    fun probe는_크기만_읽는다() {
        val src = writeJpeg("p.jpg", width = 1080, height = 1350, quality = 90)

        assertEquals(ImageBounds(1080, 1350), AndroidImageCodec.probe(src))
    }

    // ── 도구 ──

    private fun boundsOf(file: File) =
        BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { BitmapFactory.decodeFile(file.path, it) }

    /** 그라데이션 + 무작위 사각형으로 만든 테스트 이미지를 JPEG으로 쓴다. */
    private fun writeJpeg(
        name: String,
        width: Int,
        height: Int,
        quality: Int,
        config: Bitmap.Config = Bitmap.Config.ARGB_8888,
    ): File {
        val bitmap = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), Color.RED, Color.BLUE, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
        val random = Random(42)
        val rectPaint = Paint()
        repeat(200) {
            rectPaint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val x = random.nextInt(width).toFloat()
            val y = random.nextInt(height).toFloat()
            canvas.drawRect(x, y, x + width / 20f, y + height / 20f, rectPaint)
        }
        val file = File(dir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, quality, it) }
        bitmap.recycle()
        assertNotNull(file)
        return file
    }
}
