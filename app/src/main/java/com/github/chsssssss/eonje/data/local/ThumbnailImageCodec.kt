package com.github.chsssssss.eonje.data.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import com.github.chsssssss.eonje.BuildConfig
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** 썸네일 저장·정리에 쓰는 고정 값. 한곳에 모아둬서 저장 경로와 1회 정리가 같은 기준을 쓴다. */
internal object ThumbnailPolicy {
    /** 저장하는 이미지의 긴 변 상한(px). 가장 크게 보이는 곳(상세 카드, 높이 180dp)에도 충분하다. */
    const val MAX_LONG_SIDE = 800

    const val JPEG_QUALITY = 80

    /** 이보다 큰 응답은 받지 않는다. 인스타 대표 이미지는 보통 수백 KB라 정상 이미지는 걸리지 않는다. */
    const val MAX_DOWNLOAD_BYTES = 20L * 1024 * 1024

    /** 기존 파일 정리에서 긴 변이 [MAX_LONG_SIDE] 이하이고 이 크기 이하면 건드리지 않는다. */
    const val KEEP_AS_IS_BYTES = 300L * 1024

    /** 이보다 오래된 .tmp는 비정상 종료로 남은 것으로 보고 지운다. 진행 중인 다운로드를 지우지 않으려는 여유. */
    const val STALE_TMP_MILLIS = 60L * 60 * 1000

    /**
     * 디코딩 시 이미지를 2의 거듭제곱 비율로 미리 줄이는 값. 줄인 뒤에도 긴 변이 [target] 이상 남도록 해서
     * 마지막 정확한 축소(createScaledBitmap)가 품질을 유지하게 한다. 큰 원본을 통째로 올리지 않아 메모리를 아낀다.
     */
    fun sampleSizeFor(longSide: Int, target: Int = MAX_LONG_SIDE): Int {
        var sample = 1
        while (longSide / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** 기존 파일을 줄일 필요가 있는지 — 이미 충분히 작은 파일은 다시 압축하지 않는다(화질만 또 깎인다). */
    fun isAlreadySmall(width: Int, height: Int, fileBytes: Long): Boolean =
        max(width, height) <= MAX_LONG_SIDE && fileBytes <= KEEP_AS_IS_BYTES
}

internal data class ImageBounds(val width: Int, val height: Int)

internal sealed interface DownscaleResult {
    /** [dest]에 긴 변 [width]x[height]의 JPEG을 썼다. */
    data class Done(val width: Int, val height: Int) : DownscaleResult

    /** 이미지로 디코딩되지 않는다(오류 본문, 잘린 파일 등). 파일은 쓰지 않았다. */
    data object Undecodable : DownscaleResult

    /** 메모리 부족 등 일시적일 수 있는 실패. 원본이 망가진 것이 아니므로 지우면 안 된다. */
    data object Failed : DownscaleResult
}

/** 이미지 디코딩·축소·인코딩. 안드로이드 그래픽 API에 묶여 있어서 단위 테스트에선 가짜로 바꿔 끼운다. */
internal interface ImageCodec {
    /** 이미지 크기만 읽는다(픽셀은 디코딩하지 않음). 이미지가 아니면 null. */
    fun probe(file: File): ImageBounds?

    /**
     * [src]를 긴 변 [maxLongSide] 이하로 줄여 JPEG([quality])로 [dest]에 쓴다.
     * EXIF 회전은 픽셀에 반영하고(결과엔 EXIF가 없다), 투명 PNG는 흰 배경에 얹는다.
     */
    fun downscale(src: File, dest: File, maxLongSide: Int, quality: Int): DownscaleResult
}

internal object AndroidImageCodec : ImageCodec {
    private const val TAG = "ThumbnailCodec"

    override fun probe(file: File): ImageBounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return ImageBounds(options.outWidth, options.outHeight)
    }

    override fun downscale(src: File, dest: File, maxLongSide: Int, quality: Int): DownscaleResult {
        val bounds = probe(src) ?: return DownscaleResult.Undecodable
        val orientation = readOrientation(src)
        // 단계마다 새 비트맵이 만들어지고 이전 것은 해제된다. 어느 단계에서 예외가 나도 마지막으로 가진 것을 해제한다.
        var current: Bitmap? = null
        try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = ThumbnailPolicy.sampleSizeFor(max(bounds.width, bounds.height), maxLongSide)
            }
            current = BitmapFactory.decodeFile(src.path, options) ?: return DownscaleResult.Undecodable
            current = current.scaledTo(maxLongSide)
            current = current.rotatedFor(orientation)
            current = current.flattenedOnWhite()
            val result: Bitmap = current
            val written = dest.outputStream().use { result.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            if (!written) return DownscaleResult.Failed
            if (BuildConfig.DEBUG) {
                // 개수·크기만 남긴다 — URL이나 게시물 내용은 로그에 넣지 않는다.
                Log.d(
                    TAG,
                    "썸네일 저장 ${bounds.width}x${bounds.height} ${src.length() / 1024}KB → " +
                        "${result.width}x${result.height} ${dest.length() / 1024}KB",
                )
            }
            return DownscaleResult.Done(result.width, result.height)
        } catch (e: OutOfMemoryError) {
            return DownscaleResult.Failed
        } finally {
            current?.recycle()
        }
    }

    private fun readOrientation(file: File): Int = try {
        // 프레임워크 ExifInterface(API 24+)로 충분하다. minSdk 26.
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    /** 긴 변이 [maxLongSide]를 넘으면 비율을 유지해 정확히 맞춘다. 원본 비트맵은 필요 없으면 해제한다. */
    private fun Bitmap.scaledTo(maxLongSide: Int): Bitmap {
        val longSide = max(width, height)
        if (longSide <= maxLongSide) return this
        val ratio = maxLongSide.toFloat() / longSide
        val scaled = Bitmap.createScaledBitmap(
            this,
            (width * ratio).roundToInt().coerceAtLeast(1),
            (height * ratio).roundToInt().coerceAtLeast(1),
            true,
        )
        if (scaled !== this) recycle()
        return scaled
    }

    private fun Bitmap.rotatedFor(orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(-90f)
            else -> return this
        }
        val rotated = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        if (rotated !== this) recycle()
        return rotated
    }

    /** JPEG은 투명도가 없어서 그대로 압축하면 투명한 부분이 검게 나온다. */
    private fun Bitmap.flattenedOnWhite(): Bitmap {
        if (!hasAlpha()) return this
        val flat = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(flat).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@flattenedOnWhite, 0f, 0f, null)
        }
        recycle()
        return flat
    }
}
