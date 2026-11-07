package com.github.chsssssss.eonje.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 기존 썸네일 파일 하나를 정리한 결과. */
internal sealed interface CompactResult {
    /** 이미 충분히 작거나, 다시 만들어도 더 작아지지 않아서 그대로 뒀다. */
    data object Skipped : CompactResult

    /** 줄인 파일로 교체했다. */
    data class Replaced(val bytesBefore: Long, val bytesAfter: Long) : CompactResult

    /** 이미지로 디코딩되지 않아 지웠다(오류 본문이 저장된 파일 등). */
    data class Corrupt(val freedBytes: Long) : CompactResult

    /** 일시적 실패(메모리, 입출력). 파일은 그대로 뒀다. */
    data object Failed : CompactResult
}

/**
 * 인스타그램 CDN의 media_url은 시간이 지나면 만료돼서, 나중에 인박스/상세화면을 다시 열면
 * 사진이 안 보이게 된다. F2 처리 시점에 이미지를 기기 내부 저장소에 직접 받아두고
 * SavedPost.thumbnailUrl엔 file:// 경로를 남겨서 만료와 무관하게 계속 보이도록 한다.
 *
 * 받은 이미지는 [ThumbnailPolicy.MAX_LONG_SIDE]px·JPEG 품질 [ThumbnailPolicy.JPEG_QUALITY]로 줄여서 저장한다 —
 * 원본(대개 1080px대, 수백 KB)을 그대로 쌓으면 게시물이 늘수록 저장 공간을 많이 차지한다.
 */
@Singleton
class ThumbnailStore internal constructor(
    private val dir: File,
    private val okHttpClient: OkHttpClient,
    private val codec: ImageCodec,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ) : this(File(context.filesDir, "thumbnails"), okHttpClient, AndroidImageCodec)

    /**
     * [remoteUrl]의 이미지를 줄여서 기기에 저장하고 file:// 경로를 반환한다.
     * 응답이 2xx 이미지가 아니거나, 디코딩·저장에 실패하면 파일을 남기지 않고 remoteUrl을 그대로 돌려준다(최선 노력).
     *
     * 내려받은 원본과 줄인 결과를 모두 .tmp로 쓰고, 끝까지 성공했을 때만 최종 이름으로 rename한다 —
     * 중간에 예외가 나도 쓰다 만 `{key}.jpg`가 남지 않는다.
     */
    suspend fun cache(key: String, remoteUrl: String): String = withContext(Dispatchers.IO) {
        val finalFile = File(dir, "$key.jpg")
        val downloaded = File(dir, "$key.jpg.src.tmp")
        val encoded = File(dir, "$key.jpg.tmp")
        try {
            dir.mkdirs()
            if (!download(remoteUrl, downloaded)) return@withContext remoteUrl
            val result = codec.downscale(
                src = downloaded,
                dest = encoded,
                maxLongSide = ThumbnailPolicy.MAX_LONG_SIDE,
                quality = ThumbnailPolicy.JPEG_QUALITY,
            )
            if (result !is DownscaleResult.Done) return@withContext remoteUrl
            replace(encoded, finalFile)
            // Uri.fromFile(file).toString()과 같은 모양(file:///...)이다. 단위 테스트에서 안드로이드 Uri 없이 쓰려고 직접 만든다.
            "file://${finalFile.absolutePath}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            remoteUrl
        } finally {
            downloaded.delete()
            encoded.delete()
        }
    }

    /**
     * 이 기기에 [key]의 썸네일 파일이 있으면 file:// 경로, 없으면 null.
     * Firestore에는 썸네일 경로를 저장하지 않으므로(기기마다 다름) 게시물을 읽을 때 이걸로 채운다.
     */
    fun existingUrl(key: String): String? {
        val file = File(dir, "$key.jpg")
        return if (file.isFile && file.length() > 0) "file://${file.absolutePath}" else null
    }

    /**
     * [fromKey]의 썸네일을 [toKey] 이름으로 복사한다 — [toKey]에 이미 썸네일이 있으면 아무것도 하지 않는다(계정 합치기에서 버려진 익명
     * 게시물의 썸네일을 살아남는 게시물이 못 갖고 있을 때만 쓴다). 원본 파일은 남기고, 고아가 되면 정리 쪽이 지운다.
     */
    fun adopt(fromKey: String, toKey: String): Boolean {
        if (existingUrl(toKey) != null) return false
        val from = File(dir, "$fromKey.jpg")
        if (!from.isFile || from.length() == 0L) return false
        return try {
            from.copyTo(File(dir, "$toKey.jpg"), overwrite = false)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 지금 있는 썸네일 파일의 게시물 ID(파일 이름의 첫 점 앞부분) 목록. */
    fun listKeys(): Set<String> = snapshotFiles().filter { it.name.endsWith(".jpg") }.mapTo(HashSet()) { keyOf(it) }

    /**
     * [keys]의 `{key}.jpg` 중 [cutoffMillis] **이후에 바뀐 적이 없는** 것만 지운다. 이후에 바뀐 파일은 새로 받은 것(같은 게시물을 다시 저장한 다른 계정의
     * 것일 수 있다)이라 지우지 않는다. 멱등 — 없는 파일은 그냥 넘어간다. 지운 개수를 돌려준다.
     */
    fun deleteKeysNotModifiedSince(keys: Set<String>, cutoffMillis: Long): Int {
        var deleted = 0
        keys.forEach { key ->
            val file = File(dir, "$key.jpg")
            if (file.isFile && file.lastModified() <= cutoffMillis && file.delete()) deleted++
        }
        return deleted
    }

    /** 받아둔 썸네일을 전부 지운다(로그아웃·계정 삭제). */
    fun clearAll() {
        dir.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    /** 게시물 삭제 시 같이 정리한다 — 안 만들어졌어도(다운로드 실패) 조용히 넘어간다. */
    fun deleteAll(keys: List<String>) {
        keys.forEach { File(dir, "$it.jpg").delete() }
    }

    /** 응답이 2xx이고 Content-Type이 image 계열일 때만 [dest]에 받는다. 아니면 false(파일은 호출한 쪽이 지운다). */
    private fun download(url: String, dest: File): Boolean {
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return false
            val body = response.body ?: return false
            if (body.contentType()?.type != "image") return false
            if (body.contentLength() > ThumbnailPolicy.MAX_DOWNLOAD_BYTES) return false
            var total = 0L
            dest.outputStream().use { output ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        // Content-Length가 없는 응답도 상한을 넘기면 받다가 멈춘다.
                        if (total > ThumbnailPolicy.MAX_DOWNLOAD_BYTES) return false
                        output.write(buffer, 0, read)
                    }
                }
            }
            return total > 0
        }
    }

    private fun replace(from: File, to: File) {
        // 같은 파일시스템 안의 rename은 원자적이고 기존 파일을 덮어쓴다.
        if (!from.renameTo(to)) throw IOException("썸네일 파일을 교체하지 못했다")
    }

    // ── 업데이트 후 1회 정리·고아 정리용 (ThumbnailMaintenance가 호출한다) ──

    /** 폴더 안의 파일 목록. 고아 판정은 이 목록을 먼저 만든 다음 DB의 게시물 ID를 읽어야 안전하다. */
    internal fun snapshotFiles(): List<File> =
        dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()

    /**
     * 게시물 ID(파일 이름의 첫 점 앞부분)가 [validKeys]에 없는 파일을 지우고 지운 개수를 돌려준다.
     * .tmp는 게시물과 무관하게 [ThumbnailPolicy.STALE_TMP_MILLIS]보다 오래된 것만 지운다(진행 중인 저장을 건드리지 않으려고).
     */
    internal fun deleteOrphans(files: List<File>, validKeys: Set<String>, nowMillis: Long): Int {
        var deleted = 0
        for (file in files) {
            val orphan = if (file.name.endsWith(TMP_SUFFIX)) {
                nowMillis - file.lastModified() >= ThumbnailPolicy.STALE_TMP_MILLIS
            } else {
                keyOf(file) !in validKeys
            }
            if (orphan && file.delete()) deleted++
        }
        return deleted
    }

    /**
     * 기존 파일 하나를 새 기준으로 줄인다. 줄인 결과는 임시 파일에 쓰고, 더 작아졌을 때만 원본을 교체한다 —
     * 원본을 먼저 지우는 순서는 쓰지 않는다. 이미지가 아니면 지운다.
     */
    internal fun compact(file: File): CompactResult {
        val before = file.length()
        val bounds = codec.probe(file)
        if (bounds == null) {
            return if (file.delete()) CompactResult.Corrupt(before) else CompactResult.Failed
        }
        if (ThumbnailPolicy.isAlreadySmall(bounds.width, bounds.height, before)) return CompactResult.Skipped

        val tmp = File(file.parentFile, "${file.name}$TMP_SUFFIX")
        try {
            when (
                codec.downscale(
                    src = file,
                    dest = tmp,
                    maxLongSide = ThumbnailPolicy.MAX_LONG_SIDE,
                    quality = ThumbnailPolicy.JPEG_QUALITY,
                )
            ) {
                is DownscaleResult.Done -> {
                    val after = tmp.length()
                    // 이미 잘 압축된 파일은 다시 만들어도 커질 수 있다. 용량을 줄이는 게 목적이라 그땐 그대로 둔다.
                    if (after <= 0 || after >= before) return CompactResult.Skipped
                    // 다운로드·정리와 겹쳐 그 사이 원본이 지워졌으면 새로 만들지 않는다(고아 파일이 된다).
                    if (!file.exists()) return CompactResult.Skipped
                    replace(tmp, file)
                    return CompactResult.Replaced(before, after)
                }
                DownscaleResult.Undecodable ->
                    return if (file.delete()) CompactResult.Corrupt(before) else CompactResult.Failed
                DownscaleResult.Failed -> return CompactResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CompactResult.Failed
        } finally {
            tmp.delete()
        }
    }

    internal fun keyOf(file: File): String = file.name.substringBefore('.')

    private companion object {
        const val COPY_BUFFER_BYTES = 8 * 1024
        const val TMP_SUFFIX = ".tmp"
    }
}
