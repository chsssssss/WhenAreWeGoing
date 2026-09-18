package com.github.chsssssss.eonje.data.local

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 인스타그램 CDN의 media_url은 시간이 지나면 만료돼서, 나중에 인박스/상세화면을 다시 열면
 * 사진이 안 보이게 된다. F2 처리 시점에 이미지를 기기 내부 저장소에 직접 받아두고
 * SavedPost.thumbnailUrl엔 file:// 경로를 남겨서 만료와 무관하게 계속 보이도록 한다.
 */
@Singleton
class ThumbnailStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) {
    private val dir: File by lazy {
        File(context.filesDir, "thumbnails").apply { mkdirs() }
    }

    /** [remoteUrl]의 이미지를 기기에 저장하고 file:// 경로를 반환한다. 실패하면 remoteUrl을 그대로 돌려준다(최선 노력). */
    suspend fun cache(key: String, remoteUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(dir, "$key.jpg")
            okHttpClient.newCall(Request.Builder().url(remoteUrl).build()).execute().use { response ->
                val body = response.body ?: return@withContext remoteUrl
                file.outputStream().use { output -> body.byteStream().use { it.copyTo(output) } }
            }
            Uri.fromFile(file).toString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            remoteUrl
        }
    }

    /** 게시물 삭제 시 같이 정리한다 — 안 만들어졌어도(다운로드 실패) 조용히 넘어간다. */
    fun deleteAll(keys: List<String>) {
        keys.forEach { File(dir, "$it.jpg").delete() }
    }
}
