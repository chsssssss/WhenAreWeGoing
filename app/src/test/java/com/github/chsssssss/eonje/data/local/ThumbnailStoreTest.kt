package com.github.chsssssss.eonje.data.local

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * 다운로드 검증, 임시 파일 → rename 순서, 실패 시 폴백(원격 URL 반환)을 검증한다.
 * 실제 비트맵 리사이즈 결과는 안드로이드 그래픽이 필요해서 androidTest(ThumbnailImageCodecTest)에서 확인한다.
 */
class ThumbnailStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var dir: File
    private val remoteUrl = "https://cdn.example.test/image.jpg"

    @Before
    fun setUp() {
        dir = File(temp.root, "thumbnails") // 일부러 만들지 않는다 — 저장소가 알아서 만들어야 한다
    }

    @Test
    fun `정상 이미지는 줄여서 최종 이름으로 저장하고 file 경로를 돌려준다`() = runBlocking {
        val codec = FakeCodec()
        val store = store(client(200, "image/jpeg", ByteArray(5000)), codec)

        val result = store.cache("post-1", remoteUrl)

        assertEquals("file://${File(dir, "post-1.jpg").absolutePath}", result)
        assertEquals("코덱이 만든 결과가 그대로 저장돼야 한다", FakeCodec.OUTPUT.toList(), File(dir, "post-1.jpg").readBytes().toList())
        assertEquals("최종 파일 외에 .tmp가 남으면 안 된다", listOf("post-1.jpg"), dir.list()!!.toList())
        assertEquals(1, codec.downscaleCalls)
    }

    @Test
    fun `응답 코드가 2xx가 아니면 파일을 만들지 않고 원격 URL을 돌려준다`() = runBlocking {
        for (code in listOf(403, 404, 500)) {
            val codec = FakeCodec()
            val store = store(client(code, "image/jpeg", ByteArray(100)), codec)

            assertEquals(remoteUrl, store.cache("post-$code", remoteUrl))

            assertTrue("코드 $code: 파일이 남으면 안 된다", dir.list().orEmpty().isEmpty())
            assertEquals("코드 $code: 오류 본문을 디코딩하려 하면 안 된다", 0, codec.downscaleCalls)
        }
    }

    @Test
    fun `Content-Type이 이미지가 아니면 파일을 만들지 않는다`() = runBlocking {
        for (type in listOf("text/html", "application/json", "application/octet-stream", null)) {
            val store = store(client(200, type, "<html>error</html>".toByteArray()), FakeCodec())

            assertEquals("type=$type", remoteUrl, store.cache("post-x", remoteUrl))

            assertTrue("type=$type: 파일이 남으면 안 된다", dir.list().orEmpty().isEmpty())
        }
    }

    @Test
    fun `이미지로 디코딩되지 않으면 파일을 만들지 않고 폴백한다`() = runBlocking {
        val store = store(client(200, "image/jpeg", ByteArray(100)), FakeCodec { _, _ -> DownscaleResult.Undecodable })

        assertEquals(remoteUrl, store.cache("post-1", remoteUrl))

        assertTrue(dir.list().orEmpty().isEmpty())
    }

    @Test
    fun `다운로드 중간에 예외가 나면 임시 파일을 지우고 폴백한다`() = runBlocking {
        val store = store(clientWithBody(failingBody()), FakeCodec())

        assertEquals(remoteUrl, store.cache("post-1", remoteUrl))

        assertTrue("쓰다 만 파일이 남으면 안 된다: ${dir.list()?.toList()}", dir.list().orEmpty().isEmpty())
    }

    @Test
    fun `인코딩 중 예외가 나면 임시 파일을 지우고 폴백한다`() = runBlocking {
        val codec = FakeCodec { _, dest ->
            dest.writeBytes(ByteArray(10)) // 일부 쓰고
            throw IllegalStateException("인코딩 실패") // 죽는다
        }
        val store = store(client(200, "image/jpeg", ByteArray(100)), codec)

        assertEquals(remoteUrl, store.cache("post-1", remoteUrl))

        assertTrue("임시 파일이 남으면 안 된다: ${dir.list()?.toList()}", dir.list().orEmpty().isEmpty())
    }

    @Test
    fun `저장에 실패해도 이전에 저장된 파일은 건드리지 않는다`() = runBlocking {
        dir.mkdirs()
        val existing = File(dir, "post-1.jpg").apply { writeBytes(byteArrayOf(9, 9, 9)) }
        val store = store(client(404, "image/jpeg", ByteArray(100)), FakeCodec())

        store.cache("post-1", remoteUrl)

        assertEquals(listOf<Byte>(9, 9, 9), existing.readBytes().toList())
    }

    @Test
    fun `상한보다 큰 응답은 받지 않는다`() = runBlocking {
        val codec = FakeCodec()
        val tooBig = ThumbnailPolicy.MAX_DOWNLOAD_BYTES + 1
        val store = store(clientWithBody(bodyWithLength(tooBig)), codec)

        assertEquals(remoteUrl, store.cache("post-1", remoteUrl))

        assertTrue(dir.list().orEmpty().isEmpty())
        assertEquals(0, codec.downscaleCalls)
    }

    @Test
    fun `잘못된 URL이어도 예외 없이 폴백한다`() = runBlocking {
        val store = store(client(200, "image/jpeg", ByteArray(10)), FakeCodec())

        assertEquals("not a url", store.cache("post-1", "not a url"))
    }

    @Test
    fun `deleteAll은 해당 게시물의 파일만 지운다`() {
        dir.mkdirs()
        File(dir, "a.jpg").writeBytes(byteArrayOf(1))
        File(dir, "b.jpg").writeBytes(byteArrayOf(1))
        val store = store(client(200, "image/jpeg", ByteArray(1)), FakeCodec())

        store.deleteAll(listOf("a", "missing"))

        assertEquals(listOf("b.jpg"), dir.list()!!.toList())
    }

    @Test
    fun `sampleSizeFor는 줄인 뒤에도 긴 변이 목표 이상 남는 가장 큰 2의 거듭제곱이다`() {
        assertEquals(1, ThumbnailPolicy.sampleSizeFor(500))
        assertEquals(1, ThumbnailPolicy.sampleSizeFor(800))
        assertEquals(1, ThumbnailPolicy.sampleSizeFor(1350)) // 1350/2=675 < 800
        assertEquals(2, ThumbnailPolicy.sampleSizeFor(1600)) // 1600/2=800
        assertEquals(2, ThumbnailPolicy.sampleSizeFor(3000)) // 3000/4=750 < 800
        assertEquals(4, ThumbnailPolicy.sampleSizeFor(3200)) // 3200/4=800
        assertEquals(8, ThumbnailPolicy.sampleSizeFor(8000)) // 8000/8=1000, 8000/16=500
    }

    @Test
    fun `이미 작은 파일 판정은 긴 변과 용량을 둘 다 본다`() {
        assertTrue(ThumbnailPolicy.isAlreadySmall(600, 800, 100 * 1024))
        assertFalse("긴 변이 크면 용량이 작아도 줄인다", ThumbnailPolicy.isAlreadySmall(1080, 1350, 50 * 1024))
        assertFalse("긴 변이 작아도 용량이 크면 다시 압축한다", ThumbnailPolicy.isAlreadySmall(800, 800, 400 * 1024))
    }

    // ── 도구 ──

    private fun store(client: OkHttpClient, codec: ImageCodec) = ThumbnailStore(dir, client, codec)

    private fun client(code: Int, contentType: String?, body: ByteArray): OkHttpClient =
        clientWithBody(body.toResponseBody(contentType?.toMediaType()), code)

    private fun clientWithBody(body: ResponseBody, code: Int = 200): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("test")
                    .body(body)
                    .build()
            }
            .build()

    /** 일부를 보낸 뒤 연결이 끊기는 응답. */
    private fun failingBody(): ResponseBody = object : ResponseBody() {
        override fun contentType() = "image/jpeg".toMediaType()
        override fun contentLength() = -1L
        override fun source(): BufferedSource = object : Source {
            private var sent = false
            override fun read(sink: Buffer, byteCount: Long): Long {
                if (!sent) {
                    sent = true
                    sink.write(ByteArray(100))
                    return 100
                }
                throw IOException("연결 끊김")
            }

            override fun timeout() = Timeout.NONE
            override fun close() {}
        }.buffer()
    }

    /** Content-Length만 [length]로 알리는 응답(본문은 짧다). */
    private fun bodyWithLength(length: Long): ResponseBody = object : ResponseBody() {
        override fun contentType() = "image/jpeg".toMediaType()
        override fun contentLength() = length
        override fun source(): BufferedSource = Buffer().apply { write(ByteArray(10)) }
    }
}

/** 안드로이드 그래픽 없이 동작하는 가짜 코덱. 기본 동작은 고정된 바이트를 [dest]에 쓰고 성공을 돌려준다. */
internal class FakeCodec(
    var probeResult: ImageBounds? = ImageBounds(1080, 1350),
    private val onDownscale: (File, File) -> DownscaleResult = { _, dest ->
        dest.writeBytes(OUTPUT)
        DownscaleResult.Done(640, 800)
    },
) : ImageCodec {
    var downscaleCalls = 0
        private set

    override fun probe(file: File): ImageBounds? = probeResult

    override fun downscale(src: File, dest: File, maxLongSide: Int, quality: Int): DownscaleResult {
        downscaleCalls++
        return onDownscale(src, dest)
    }

    companion object {
        val OUTPUT = byteArrayOf(1, 2, 3, 4)
    }
}
