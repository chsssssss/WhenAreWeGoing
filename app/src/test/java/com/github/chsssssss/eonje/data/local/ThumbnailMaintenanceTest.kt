package com.github.chsssssss.eonje.data.local

import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** 고아 파일 정리와 기존 썸네일 1회 정리(깨진 파일 / 이미 작은 파일 / 큰 파일 / 실패 계속 진행)를 검증한다. */
class ThumbnailMaintenanceTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var dir: File
    private val now = 1_800_000_000_000L

    @Before
    fun setUp() {
        dir = temp.newFolder("thumbnails")
    }

    // ── 고아 파일 정리 ──

    @Test
    fun `게시물이 없는 파일만 지우고 있는 파일은 둔다`() = runBlocking {
        file("a.jpg", 10)
        file("b.jpg", 10) // 게시물 b 없음 → 고아
        file("c.png", 10) // 게시물 c 없음 → 고아
        val maintenance = maintenance(posts = listOf(post("a")), state = FakeState(done = true))

        val report = maintenance.run(now)

        assertEquals(listOf("a.jpg"), dir.list()!!.toList())
        assertEquals(2, report.orphansDeleted)
        assertNull("이미 정리한 상태라 1회 정리는 건너뛴다", report.compaction)
    }

    @Test
    fun `Room 이전이 안 끝났으면 일부만 옮겨진 상태라 고아로 보이는 파일도 지우지 않는다`() = runBlocking {
        file("migrated.jpg", 10) // 이미 Firestore로 옮겨진 게시물
        file("notYet.jpg", 10) // 아직 안 옮겨진 게시물 — ID 목록엔 없지만 고아가 아니다. 썸네일은 다시 받을 수 없다
        file("old.jpg.tmp", 10).setLastModified(now - ThumbnailPolicy.STALE_TMP_MILLIS - 1)
        val posts = FakePosts(listOf(post("migrated")))
        val maintenance = maintenance(posts, FakeState(done = true), legacy = FakeLegacy(complete = false))

        val report = maintenance.run(now)

        assertTrue("이전이 끝나기 전엔 서버 확인도 하지 않는다", posts.serverChecks.isEmpty())
        assertEquals(listOf("migrated.jpg", "notYet.jpg"), dir.list()!!.sorted())
        assertEquals("오래된 tmp만 지운다", 1, report.orphansDeleted)
    }

    // ── 서버 확인: 캐시 기준 의심 파일은 서버에도 없을 때만 지운다 ──

    @Test
    fun `캐시에는 없지만 서버에 있는 게시물의 파일은 지우지 않는다`() = runBlocking {
        file("cached.jpg", 10)
        file("evicted.jpg", 10) // 캐시 정리로 빠졌을 뿐 서버에 있는 게시물 — 썸네일은 복구할 수 없다
        file("gone.jpg", 10) // 서버에도 없다
        val posts = FakePosts(listOf(post("cached")), serverOnlyIds = setOf("evicted"))

        val report = maintenance(posts, FakeState(done = true)).run(now)

        assertEquals(listOf("cached.jpg", "evicted.jpg"), dir.list()!!.sorted())
        assertEquals(1, report.orphansDeleted)
        assertEquals("의심 파일 둘만 서버에 묻는다", listOf(listOf("evicted", "gone")), posts.serverChecks)
    }

    @Test
    fun `서버에도 없다고 확인된 파일만 지운다`() = runBlocking {
        file("a.jpg", 10)
        file("x.jpg", 10)
        file("y.png", 10)
        val posts = FakePosts(listOf(post("a")))

        val report = maintenance(posts, FakeState(done = true)).run(now)

        assertEquals(listOf("a.jpg"), dir.list()!!.toList())
        assertEquals(2, report.orphansDeleted)
    }

    @Test
    fun `오프라인이거나 서버 확인에 실패하면 의심 파일을 지우지 않는다`() = runBlocking {
        file("a.jpg", 10)
        file("x.jpg", 10)
        file("old.jpg.tmp", 10).setLastModified(now - ThumbnailPolicy.STALE_TMP_MILLIS - 1)
        val posts = FakePosts(listOf(post("a")), offline = true)

        val report = maintenance(posts, FakeState(done = true)).run(now)

        assertEquals("x.jpg는 다음 기회로 미룬다", listOf("a.jpg", "x.jpg"), dir.list()!!.sorted())
        assertEquals("서버와 무관한 오래된 tmp는 그대로 정리", 1, report.orphansDeleted)
        assertEquals(1, posts.serverChecks.size)
    }

    @Test
    fun `의심 파일이 없으면 서버를 부르지 않는다`() = runBlocking {
        file("a.jpg", 10)
        file("b.jpg", 10)
        val posts = FakePosts(listOf(post("a"), post("b")))

        maintenance(posts, FakeState(done = true)).run(now)

        assertTrue("서버 읽기가 0이어야 한다", posts.serverChecks.isEmpty())
        assertEquals(listOf("a.jpg", "b.jpg"), dir.list()!!.sorted())
    }

    @Test
    fun `한 번에 확인하는 의심 파일에는 상한이 있고 나머지는 지키고 다음에 본다`() = runBlocking {
        file("keep.jpg", 10)
        val suspects = (1..75).map { "s%03d".format(it) }
        suspects.forEach { file("$it.jpg", 10) }
        val posts = FakePosts(listOf(post("keep")))

        val report = maintenance(posts, FakeState(done = true)).run(now)

        assertEquals("상한 60개만 서버에 묻는다", listOf(suspects.take(60)), posts.serverChecks)
        assertEquals(60, report.orphansDeleted)
        assertEquals("확인하지 못한 15개는 남는다", suspects.drop(60).map { "$it.jpg" }, dir.list()!!.sorted().filter { it != "keep.jpg" })
    }

    @Test
    fun `Room 이전이 끝나면 다시 고아를 지운다`() = runBlocking {
        file("a.jpg", 10)
        file("gone.jpg", 10)
        maintenance(FakePosts(listOf(post("a"))), FakeState(done = true), legacy = FakeLegacy(complete = true)).run(now)

        assertEquals(listOf("a.jpg"), dir.list()!!.toList())
    }

    @Test
    fun `오래된 tmp는 지우고 방금 만든 tmp는 진행 중일 수 있어 둔다`() = runBlocking {
        file("a.jpg.src.tmp", 10).setLastModified(now - ThumbnailPolicy.STALE_TMP_MILLIS - 1)
        file("a.jpg.tmp", 10).setLastModified(now - 1_000)
        val maintenance = maintenance(posts = listOf(post("a")), state = FakeState(done = true))

        maintenance.run(now)

        assertEquals(listOf("a.jpg.tmp"), dir.list()!!.toList())
    }

    @Test
    fun `고아 정리는 게시물 행을 지우지 않는다`() = runBlocking {
        file("orphan.jpg", 10)
        val posts = FakePosts(listOf(post("a")))
        maintenance(posts = posts, state = FakeState(done = true)).run(now)

        assertEquals(listOf("a"), posts.all.map { it.id })
        assertTrue(posts.deletedIds.isEmpty())
    }

    @Test
    fun `폴더가 없어도 안전하다`() = runBlocking {
        dir.deleteRecursively()
        val report = maintenance(posts = emptyList(), state = FakeState(done = false)).run(now)

        assertEquals(0, report.orphansDeleted)
        assertEquals(CompactionReport(0, 0), report.compaction)
    }

    // ── 기존 파일 1회 정리 ──

    @Test
    fun `깨진 파일은 지우고 그 게시물의 로컬 썸네일 경로를 비운다`() = runBlocking {
        file("bad.jpg", 700)
        file("good.jpg", 50 * 1024)
        val codec = codec { file -> if (file.name == "bad.jpg") null else ImageBounds(600, 800) }
        val posts = FakePosts(listOf(post("bad"), post("good")))
        val maintenance = maintenance(posts, FakeState(), codec)

        val report = maintenance.run(now)

        assertEquals(listOf("good.jpg"), dir.list()!!.toList())
        assertEquals(listOf("bad"), posts.clearedIds)
        assertEquals(CompactionReport(processedFiles = 1, savedBytes = 700), report.compaction)
    }

    @Test
    fun `이미 작은 파일은 건드리지 않는다`() = runBlocking {
        val small = file("small.jpg", 100 * 1024).also { it.writeBytes(ByteArray(100 * 1024) { 7 }) }
        val codec = codec { ImageBounds(600, 800) }
        val maintenance = maintenance(listOf(post("small")), FakeState(), codec)

        val report = maintenance.run(now)

        assertEquals("재인코딩 호출 자체가 없어야 한다", 0, codec.downscaleCalls)
        assertTrue(small.readBytes().all { it == 7.toByte() })
        assertEquals(CompactionReport(0, 0), report.compaction)
    }

    @Test
    fun `큰 파일은 줄인 파일로 교체하고 줄어든 용량을 합산한다`() = runBlocking {
        val big = file("big.jpg", 400 * 1024)
        val codec = codec { ImageBounds(1080, 1350) } // 기본 동작: 4바이트짜리 결과를 쓴다
        val maintenance = maintenance(listOf(post("big")), FakeState(), codec)

        val report = maintenance.run(now)

        assertEquals(FakeCodec.OUTPUT.toList(), big.readBytes().toList())
        assertEquals(CompactionReport(1, 400L * 1024 - FakeCodec.OUTPUT.size), report.compaction)
        assertEquals("교체 후 .tmp가 남으면 안 된다", listOf("big.jpg"), dir.list()!!.toList())
    }

    @Test
    fun `긴 변은 크지만 용량이 작은 파일은 다시 만든 결과가 더 크면 원본을 둔다`() = runBlocking {
        val original = file("compressed.jpg", 2).also { it.writeBytes(byteArrayOf(5, 5)) }
        val codec = codec { ImageBounds(1080, 1350) } // 결과(4바이트)가 원본(2바이트)보다 크다
        val maintenance = maintenance(listOf(post("compressed")), FakeState(), codec)

        val report = maintenance.run(now)

        assertEquals(listOf<Byte>(5, 5), original.readBytes().toList())
        assertEquals(CompactionReport(0, 0), report.compaction)
        assertEquals(listOf("compressed.jpg"), dir.list()!!.toList())
    }

    @Test
    fun `한 파일이 실패해도 다음 파일로 계속 진행하고 원본은 그대로 둔다`() = runBlocking {
        val first = file("a.jpg", 400 * 1024).also { it.writeBytes(ByteArray(10) { 1 }) }
        val second = file("b.jpg", 400 * 1024)
        val codec = FakeCodec { src, dest ->
            if (src.name == "a.jpg") {
                dest.writeBytes(ByteArray(3)) // 쓰다가
                throw IllegalStateException("인코딩 실패") // 실패
            } else {
                dest.writeBytes(FakeCodec.OUTPUT)
                DownscaleResult.Done(640, 800)
            }
        }
        val maintenance = maintenance(listOf(post("a"), post("b")), FakeState(), codec)

        val report = maintenance.run(now)

        assertEquals("실패한 파일의 원본은 손대지 않는다", ByteArray(10) { 1 }.toList(), first.readBytes().toList())
        assertEquals(FakeCodec.OUTPUT.toList(), second.readBytes().toList())
        assertEquals("실패한 파일의 .tmp가 남으면 안 된다", listOf("a.jpg", "b.jpg"), dir.list()!!.sorted())
        assertEquals(1, report.compaction!!.processedFiles)
    }

    @Test
    fun `메모리 부족 같은 일시적 실패는 파일을 지우지 않는다`() = runBlocking {
        file("a.jpg", 400 * 1024)
        val codec = FakeCodec { _, _ -> DownscaleResult.Failed }
        val posts = FakePosts(listOf(post("a")))

        maintenance(posts, FakeState(), codec).run(now)

        assertEquals(listOf("a.jpg"), dir.list()!!.toList())
        assertTrue("썸네일 경로도 비우지 않는다", posts.clearedIds.isEmpty())
    }

    @Test
    fun `디코딩이 끝내 안 되는 파일은 probe를 통과해도 지운다`() = runBlocking {
        file("trunc.jpg", 400 * 1024)
        val codec = FakeCodec { _, _ -> DownscaleResult.Undecodable }
        val posts = FakePosts(listOf(post("trunc")))

        val report = maintenance(posts, FakeState(), codec).run(now)

        assertTrue(dir.list()!!.isEmpty())
        assertEquals(listOf("trunc"), posts.clearedIds)
        assertEquals(1, report.compaction!!.processedFiles)
    }

    @Test
    fun `1회 정리는 한 번만 돌고 완료가 기록된다`() = runBlocking {
        file("big.jpg", 400 * 1024)
        val codec = codec { ImageBounds(1080, 1350) }
        val state = FakeState()
        val maintenance = maintenance(listOf(post("big")), state, codec)

        val first = maintenance.run(now)
        // 두 번째 실행엔 다시 큰 파일이 있어도(새로 저장된 것처럼) 1회 정리는 하지 않는다.
        file("big2.jpg", 400 * 1024)
        val second = maintenance(listOf(post("big"), post("big2")), state, codec).run(now)

        assertTrue(state.done)
        assertEquals(1, codec.downscaleCalls)
        assertEquals(1, first.compaction!!.processedFiles)
        assertNull(second.compaction)
    }

    @Test
    fun `로컬 데이터가 비어 있으면 파일 이름으로 고아를 판단하지 않는다`() = runBlocking {
        file("a.jpg", 10)
        file("old.jpg.tmp", 10).setLastModified(now - ThumbnailPolicy.STALE_TMP_MILLIS - 1)

        val report = maintenance(emptyList(), FakeState(done = true)).run(now)

        assertEquals("게시물이 없는 게 아니라 아직 로컬에 못 올라온 걸 수 있어 썸네일은 지우지 않는다", listOf("a.jpg"), dir.list()!!.toList())
        assertEquals("오래된 tmp만 정리한다", 1, report.orphansDeleted)
    }

    @Test
    fun `고아 파일은 줄이지 않고 지운다`() = runBlocking {
        file("orphan.jpg", 400 * 1024)
        val codec = codec { ImageBounds(1080, 1350) }

        val report = maintenance(listOf(post("other")), FakeState(), codec).run(now)

        assertEquals(0, codec.downscaleCalls)
        assertEquals(1, report.orphansDeleted)
        assertEquals(CompactionReport(0, 0), report.compaction)
    }

    @Test
    fun `DB 쪽 경로 비우기가 실패해도 다음 파일로 계속 진행한다`() = runBlocking {
        file("bad1.jpg", 10)
        file("bad2.jpg", 10)
        val codec = codec { null }
        val posts = FakePosts(listOf(post("bad1"), post("bad2")), failClearFor = "bad1")

        val report = maintenance(posts, FakeState(), codec).run(now)

        assertTrue(dir.list()!!.isEmpty())
        assertEquals(listOf("bad2"), posts.clearedIds)
        assertEquals(2, report.compaction!!.processedFiles)
    }

    @Test
    fun `취소는 삼키지 않고 완료로 기록하지 않는다`() {
        file("a.jpg", 400 * 1024)
        val codec = FakeCodec { _, _ -> throw CancellationException("취소") }
        val state = FakeState()
        // compact 안에서 CancellationException은 그대로 전파돼야 한다.
        val thrown = runCatching {
            runBlocking { maintenance(listOf(post("a")), state, codec).run(now) }
        }.exceptionOrNull()

        assertTrue("취소가 전파돼야 한다: $thrown", thrown is CancellationException)
        assertFalse("취소된 정리는 완료로 기록하면 안 된다", state.done)
    }

    // ── 도구 ──

    private fun file(name: String, size: Int): File = File(dir, name).apply { writeBytes(ByteArray(size)) }

    private fun post(id: String) = SavedPost(
        id = id,
        shortcode = id,
        instagramUrl = "https://www.instagram.com/p/$id/",
        caption = null,
        thumbnailUrl = "file://${File(dir, "$id.jpg").absolutePath}",
        status = ResolveStatus.RESOLVED,
        extractedCount = 0,
        createdAt = 0L,
        unresolvedReason = null as UnresolvedReason?,
    )

    private fun codec(probe: (File) -> ImageBounds?) = FakeCodec().also { it.probeFor = probe }

    private fun maintenance(posts: List<SavedPost>, state: FakeState, codec: FakeCodec = FakeCodec()) =
        maintenance(FakePosts(posts), state, codec)

    private fun maintenance(
        posts: FakePosts,
        state: FakeState,
        codec: FakeCodec = FakeCodec(),
        legacy: FakeLegacy = FakeLegacy(complete = true),
    ) = ThumbnailMaintenance(ThumbnailStore(dir, OkHttpClient(), codec), posts, state, legacy)
}

private class FakeLegacy(var complete: Boolean) : LegacyMigrationStatus {
    override suspend fun isComplete() = complete
    override suspend fun isLegacyDuplicate(shortcode: String) = false
}

private class FakeState(var done: Boolean = false) : ThumbnailMaintenanceState {
    override suspend fun isCompactionDone() = done
    override suspend fun markCompactionDone() {
        done = true
    }
}

private class FakePosts(
    /** 캐시에 있는 게시물(서버에도 있다). */
    val all: List<SavedPost>,
    private val failClearFor: String? = null,
    /** 캐시엔 없지만 서버엔 있는 게시물 id(캐시 정리·부분 동기화로 빠진 경우). */
    private val serverOnlyIds: Set<String> = emptySet(),
    /** 오프라인이면 서버가 대답하지 못한다. */
    private val offline: Boolean = false,
) : SavedPostRepository {
    val clearedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()

    /** 서버 확인으로 넘어온 id 목록(호출마다 하나). */
    val serverChecks = mutableListOf<List<String>>()

    override suspend fun findIdsAbsentOnServer(ids: Collection<String>): Set<String> {
        serverChecks += ids.toList()
        if (offline) return emptySet() // 대답을 못 받았으니 "없다고 확인된" 것이 없다
        val onServer = all.mapTo(HashSet()) { it.id } + serverOnlyIds
        return ids.filterNotTo(HashSet()) { it in onServer }
    }

    override fun observeAll(): Flow<List<SavedPost>> = flowOf(all)

    override suspend fun cachedPostIds(): Set<String> = all.mapTo(HashSet()) { it.id }

    override suspend fun loadCachedUnresolved(): List<SavedPost> = emptyList()

    override suspend fun clearLocalThumbnail(id: String) {
        if (id == failClearFor) throw IllegalStateException("DB 오류")
        clearedIds += id
    }

    override suspend fun deleteByIds(ids: List<String>) {
        deletedIds += ids
    }

    override suspend fun findByShortcode(shortcode: String): SavedPost? = null
    override suspend fun findById(id: String): SavedPost? = null
    override suspend fun findByIds(ids: List<String>): List<SavedPost> = emptyList()
    override suspend fun save(post: SavedPost) = Unit
    override suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?) = Unit
    override suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int) = Unit
    override fun observeUnresolved(): Flow<List<SavedPost>> = flowOf(emptyList())
}
