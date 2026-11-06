package com.github.chsssssss.eonje.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.local.AndroidImageCodec
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import com.google.firebase.firestore.MemoryLruGcSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/**
 * Firestore 구현의 핵심 동작을 Firebase Emulator Suite(Auth+Firestore)에 붙여서 확인한다.
 * 호스트 PC에서 먼저 `scripts/start-emulators.sh`(= `firebase emulators:start --only auth,firestore`, 프로젝트는 .firebaserc 기본값 = 앱과 같은 ID)가 떠 있어야 한다
 * (안드로이드 에뮬레이터에서 호스트는 10.0.2.2). 안 떠 있으면 테스트는 건너뛴다(실패 처리하지 않음).
 *
 * 테스트마다 새 FirebaseApp과 새 익명 사용자(uid)를 쓰므로 서로의 데이터가 섞이지 않는다. firestore.rules가 적용된 상태다.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRepositoriesTest {

    private lateinit var scope: CoroutineScope
    private lateinit var session: AuthSession
    private lateinit var thumbnails: ThumbnailStore
    private lateinit var dir: File
    private var notified = 0
    private lateinit var uid: String

    private lateinit var posts: FirestoreSavedPostRepository
    private lateinit var places: FirestorePlaceRepository
    private lateinit var folders: FirestoreFolderRepository
    private lateinit var candidates: FirestoreExtractedCandidateRepository

    // FirebaseApp·에뮬레이터 연결은 프로세스에서 한 번만 만든다(Auth SDK가 전역 상태를 첫 앱에 묶어서, 앱을 지웠다 다시 만들면 깨진다).
    // 테스트 사이의 격리는 테스트마다 새 익명 사용자(uid)를 쓰는 것으로 한다 — 데이터가 users/{uid} 아래라서 서로 안 섞인다.
    private val firestore: FirebaseFirestore get() = shared.firestore
    private val auth: FirebaseAuth get() = shared.auth

    @Before
    fun setUp() = runBlocking<Unit> {
        assumeTrue("Firebase Emulator Suite(auth $AUTH_PORT, firestore $FIRESTORE_PORT)가 호스트에서 실행 중이어야 한다", emulatorReachable())
        firestore.enableNetwork().await() // 앞선 테스트가 오프라인으로 끝났어도 온라인에서 시작
        auth.signOut()
        uid = auth.signInAnonymously().await().user!!.uid

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        session = AuthSession(auth, scope)
        dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "firestore-test-${System.nanoTime()}")
            .apply { mkdirs() }
        thumbnails = ThumbnailStore(dir, OkHttpClient(), AndroidImageCodec)
        val notifier = PlacesChangedNotifier { notified++ }
        posts = FirestoreSavedPostRepository(firestore, session, thumbnails, scope)
        places = FirestorePlaceRepository(firestore, session, thumbnails, notifier, scope)
        folders = FirestoreFolderRepository(firestore, session, notifier, scope)
        candidates = FirestoreExtractedCandidateRepository(firestore, session)
    }

    @After
    fun tearDown() {
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
        if (shared.isStarted) runCatching { runBlocking { firestore.enableNetwork().await() } }
    }

    // ── 폴더 ──

    @Test
    fun 폴더를_지우면_그_폴더의_장소는_미분류가_되고_다른_폴더_장소는_그대로다() = runBlocking {
        val doomed = folders.createWithAppearance("지울 폴더", 0xFF1E88E5.toInt(), "☕")
        val kept = folders.findOrCreateByName("남길 폴더")
        places.save(place("A", folderId = doomed.id))
        places.save(place("B", folderId = doomed.id))
        places.save(place("C", folderId = kept.id))
        places.save(place("D", folderId = null))

        folders.delete(doomed.id)

        val byId = awaitPlaces { list -> list.first { it.id == "A" }.folderId == null }.associateBy { it.id }
        assertNull(byId.getValue("A").folderId)
        assertNull(byId.getValue("B").folderId)
        assertEquals(kept.id, byId.getValue("C").folderId)
        assertNull(byId.getValue("D").folderId)
        assertEquals(listOf("남길 폴더"), awaitFolders { it.size == 1 }.map { it.name })
        assertTrue("위젯 갱신 알림이 가야 한다", notified > 0)
        // 서버에도 그대로 반영된다.
        firestore.waitForPendingWrites().await()
        assertNull(serverDoc("places/A")!!.opt("folderId")?.takeIf { it != JSONObject.NULL })
        assertNull("폴더 문서도 지워졌다", serverDoc("folders/${doomed.id}"))
    }

    @Test
    fun 폴더가_많아도_나눠서_전부_미분류로_돌린다() = runBlocking {
        val folder = folders.findOrCreateByName("큰 폴더")
        val count = BATCH_LIMIT + 30 // 배치 한 번(500건)을 넘기는 양
        repeat(count) { places.save(place("P$it", folderId = folder.id)) }

        folders.delete(folder.id)

        val deadline = System.currentTimeMillis() + 30_000
        var latest = places.observeAll().first()
        while (System.currentTimeMillis() < deadline && !(latest.size == count && latest.all { it.folderId == null })) {
            delay(200)
            latest = places.observeAll().first()
        }
        assertEquals("장소 수", count, latest.size)
        assertEquals("아직 폴더가 남은 장소 수", 0, latest.count { it.folderId != null })
        val all = latest
        firestore.waitForPendingWrites().await()
        assertNull("폴더 문서는 마지막에 지워진다", serverDoc("folders/${folder.id}"))
    }

    @Test
    fun 같은_이름의_폴더는_새로_만들지_않고_외형만_갱신한다() = runBlocking {
        val first = folders.createWithAppearance("카페", 0xFFE53935.toInt(), "📍")

        val updated = folders.createWithAppearance("카페", 0xFF43A047.toInt(), "☕")
        val reused = folders.findOrCreateByName("  카페 ")

        assertEquals(first.id, updated.id)
        assertEquals(first.id, reused.id)
        val stored = awaitFolders { it.firstOrNull()?.iconKey == "☕" }
        assertEquals(1, stored.size)
        assertEquals(0xFF43A047.toInt(), stored.single().color)
    }

    // ── 장소 ──

    @Test
    fun 장소를_지우면_게시물의_placeIds에서_빠지고_게시물은_남는다() = runBlocking {
        posts.save(post("post-1", status = ResolveStatus.RESOLVED))
        places.save(place("A"))
        places.save(place("B"))
        places.linkPostToPlace("post-1", "A")
        places.linkPostToPlace("post-1", "B")
        awaitUntil { places.postIdsForPlace("A") == listOf("post-1") }

        places.delete("A")

        awaitUntil { places.postIdsForPlace("A").isEmpty() }
        assertEquals(listOf("post-1"), places.postIdsForPlace("B"))
        assertNotNull("게시물은 지워지지 않는다", posts.findById("post-1"))
        assertEquals(listOf("B"), awaitPlaces { it.size == 1 }.map { it.id })
        firestore.waitForPendingWrites().await()
        assertNull("서버에서도 장소 문서가 지워졌다", serverDoc("places/A"))
        assertEquals(listOf("B"), serverDoc("posts/post-1")!!.stringArray("placeIds"))
    }

    @Test
    fun 같은_카카오_장소는_새로_만들지_않고_기존_id를_돌려준다() = runBlocking {
        val first = places.save(place("first", kakaoPlaceId = "kakao-1"))

        val second = places.save(place("second", kakaoPlaceId = "kakao-1"))
        val third = places.save(place("third", kakaoPlaceId = "kakao-2"))

        assertEquals(first, second)
        assertEquals("third", third)
        assertEquals(setOf("first", "third"), awaitPlaces { it.size == 2 }.map { it.id }.toSet())
    }

    @Test
    fun 장소별_게시물은_RESOLVED만_장소마다_묶고_썸네일은_이_기기의_파일로_채운다() = runBlocking {
        File(dir, "post-1.jpg").writeBytes(ByteArray(10) { 1 }) // 이 기기에 받아둔 썸네일
        posts.save(post("post-1", status = ResolveStatus.RESOLVED, createdAt = 2_000))
        posts.save(post("post-2", status = ResolveStatus.RESOLVED, createdAt = 1_000))
        posts.save(post("post-3", status = ResolveStatus.NEEDS_REVIEW, createdAt = 3_000))
        places.save(place("A"))
        places.save(place("B"))
        places.linkPostToPlace("post-1", "A")
        places.linkPostToPlace("post-2", "A")
        places.linkPostToPlace("post-2", "B")
        places.linkPostToPlace("post-3", "B") // 미확정 게시물은 묶음에 들어오지 않는다

        val byPlace = withTimeout(TIMEOUT) { places.observePostsByPlace().first { it["A"]?.size == 2 && it["B"]?.size == 1 } }

        assertEquals(listOf("post-1", "post-2"), byPlace.getValue("A").map { it.id }) // 최신순
        assertEquals(listOf("post-2"), byPlace.getValue("B").map { it.id })
        assertEquals("file://${File(dir, "post-1.jpg").absolutePath}", byPlace.getValue("A").first().thumbnailUrl)
        assertNull("파일이 없으면 썸네일은 null", byPlace.getValue("A").last().thumbnailUrl)
    }

    // ── 게시물 ──

    @Test
    fun UNRESOLVED로_바꾸면_extractedCount가_0이_되고_다른_상태에서는_유지된다() = runBlocking {
        posts.save(post("p1", extractedCount = 3))
        posts.save(post("p2", extractedCount = 3))

        posts.updateStatus("p1", ResolveStatus.UNRESOLVED, UnresolvedReason.PLACE_NOT_FOUND)
        posts.updateStatus("p2", ResolveStatus.NEEDS_REVIEW, null)

        awaitUntil { posts.findById("p1")?.status == ResolveStatus.UNRESOLVED }
        val p1 = posts.findById("p1")!!
        assertEquals(0, p1.extractedCount)
        assertEquals(UnresolvedReason.PLACE_NOT_FOUND, p1.unresolvedReason)
        val p2 = posts.findById("p2")!!
        assertEquals(3, p2.extractedCount)
        assertNull(p2.unresolvedReason)
        firestore.waitForPendingWrites().await()
        assertEquals(0L, serverDoc("posts/p1")!!.longField("extractedCount"))
        assertEquals(3L, serverDoc("posts/p2")!!.longField("extractedCount"))
    }

    @Test
    fun 미확정_게시물_목록은_RESOLVED를_빼고_최신순이다() = runBlocking {
        posts.save(post("old", status = ResolveStatus.PENDING, createdAt = 1_000))
        posts.save(post("new", status = ResolveStatus.UNRESOLVED, createdAt = 3_000))
        posts.save(post("mid", status = ResolveStatus.NEEDS_REVIEW, createdAt = 2_000))
        posts.save(post("done", status = ResolveStatus.RESOLVED, createdAt = 4_000))

        val list = withTimeout(TIMEOUT) { posts.observeUnresolved().first { it.size == 3 } }

        assertEquals(listOf("new", "mid", "old"), list.map { it.id })
        // 상태가 RESOLVED로 바뀌면 목록에서 빠진다.
        posts.updateStatus("mid", ResolveStatus.RESOLVED, null)
        assertEquals(listOf("new", "old"), withTimeout(TIMEOUT) { posts.observeUnresolved().first { it.size == 2 } }.map { it.id })
        // 읽기 전용(위젯·알림 워커용) 캐시 조회도 같은 결과다.
        assertEquals(listOf("new", "old"), posts.loadCachedUnresolved().map { it.id })
    }

    @Test
    fun shortcode_중복_확인은_이_기기의_로컬_데이터에서_즉시_한다() = runBlocking {
        assertNull(posts.findByShortcode("ABC"))

        posts.save(post("p1", shortcode = "ABC"))

        assertEquals("p1", posts.findByShortcode("ABC")?.id)
        assertNull(posts.findByShortcode("OTHER"))
    }

    @Test
    fun 게시물을_다시_저장해도_연결된_placeIds와_후보는_지워지지_않는다() = runBlocking {
        posts.save(post("p1", status = ResolveStatus.NEEDS_REVIEW))
        places.save(place("A"))
        places.linkPostToPlace("p1", "A")
        candidates.replaceForPost("p1", listOf(candidate("p1", 0, 0, "후보")))
        awaitUntil { candidates.observeForPost("p1").first().size == 1 && places.postIdsForPlace("A") == listOf("p1") }

        posts.save(post("p1", status = ResolveStatus.NEEDS_REVIEW, caption = "다시 저장"))

        awaitUntil { posts.findById("p1")?.caption == "다시 저장" }
        assertEquals(listOf("p1"), places.postIdsForPlace("A"))
        assertEquals(1, candidates.observeForPost("p1").first().size)
    }

    @Test
    fun 게시물을_지우면_문서와_이_기기의_썸네일_파일이_같이_지워진다() = runBlocking {
        File(dir, "gone.jpg").writeBytes(ByteArray(5) { 1 })
        posts.save(post("gone"))
        posts.save(post("stay"))

        posts.deleteByIds(listOf("gone"))

        awaitUntil { posts.findById("gone") == null }
        assertFalse(File(dir, "gone.jpg").exists())
        assertNotNull(posts.findById("stay"))
    }

    @Test
    fun 문서에는_memo와_썸네일_경로가_없고_createdAt은_기기_시각_그대로다() = runBlocking {
        posts.save(post("p1", createdAt = 1_700_000_123_456L, thumbnailUrl = "file:///data/x/p1.jpg"))
        places.save(place("A", createdAt = 1_700_000_999_000L))
        firestore.waitForPendingWrites().await()

        val postDoc = serverDoc("posts/p1")!!
        val placeDoc = serverDoc("places/A")!!

        assertFalse(postDoc.hasField("thumbnailUrl"))
        assertFalse(placeDoc.hasField("memo"))
        assertEquals(1_700_000_123_456L, postDoc.longField("createdAt"))
        assertEquals("정수(밀리초)로 저장되고 서버 시각이 아니다", 1_700_000_999_000L, placeDoc.longField("createdAt"))
        assertEquals("RESOLVED", placeDoc.stringField("status")) // enum은 문자열
    }

    // ── 후보 ──

    @Test
    fun 후보는_게시물_문서의_배열에_저장되고_교체하거나_비우면_바뀐다() = runBlocking {
        posts.save(post("p1", status = ResolveStatus.NEEDS_REVIEW))
        val first = listOf(candidate("p1", 1, 1, "B2"), candidate("p1", 0, 0, "A1"), candidate("p1", 1, 0, "B1"))

        candidates.replaceForPost("p1", first)

        val read = withTimeout(TIMEOUT) { candidates.observeForPost("p1").first { it.size == 3 } }
        assertEquals(listOf("A1", "B1", "B2"), read.map { it.place.name }) // extractionIndex, rank 순
        assertEquals(first.toSet(), read.toSet())

        candidates.replaceForPost("p1", listOf(candidate("p1", 0, 0, "새 후보")))
        assertEquals(listOf("새 후보"), withTimeout(TIMEOUT) { candidates.observeForPost("p1").first { it.size == 1 } }.map { it.place.name })

        candidates.replaceForPost("p1", emptyList())
        assertTrue(withTimeout(TIMEOUT) { candidates.observeForPost("p1").first { it.isEmpty() } }.isEmpty())
        firestore.waitForPendingWrites().await()
        assertEquals(emptyList<String>(), serverDoc("posts/p1")!!.stringArray("candidates"))
    }

    // ── 오프라인 ──

    @Test
    fun 오프라인에서도_저장이_막히지_않고_온라인이_되면_서버에_올라간다() = runBlocking {
        firestore.disableNetwork().await() // 비행기 모드

        // 쓰기가 서버 응답을 기다리면 여기서 제한 시간을 넘긴다.
        withTimeout(OFFLINE_WRITE_TIMEOUT) {
            posts.save(post("offline-post", shortcode = "OFF", createdAt = 1_700_000_000_001L))
            places.save(place("offline-place", kakaoPlaceId = "k-off"))
            folders.createWithAppearance("오프라인 폴더", 0xFF5E35B1.toInt(), "⭐")
            posts.updateStatus("offline-post", ResolveStatus.UNRESOLVED, UnresolvedReason.NETWORK_ERROR)
            places.linkPostToPlace("offline-post", "offline-place")
        }

        // 화면(리스너)에는 바로 보인다.
        assertEquals(listOf("offline-post"), withTimeout(TIMEOUT) { posts.observeUnresolved().first { it.isNotEmpty() } }.map { it.id })
        assertEquals(listOf("offline-place"), places.loadCachedAll().map { it.id })
        assertEquals(listOf("오프라인 폴더"), folders.loadCachedAll().map { it.name })
        assertEquals("OFF", posts.findByShortcode("OFF")?.shortcode)
        // 하지만 서버에는 아직 없다.
        assertNull(serverDoc("posts/offline-post"))
        assertNull(serverDoc("places/offline-place"))

        firestore.enableNetwork().await() // 온라인 전환
        firestore.waitForPendingWrites().await()

        val postDoc = serverDoc("posts/offline-post")
        assertNotNull("오프라인에서 저장한 게시물이 서버에 올라갔다", postDoc)
        assertEquals("UNRESOLVED", postDoc!!.stringField("status"))
        assertEquals(0L, postDoc.longField("extractedCount"))
        assertEquals(1_700_000_000_001L, postDoc.longField("createdAt"))
        assertEquals(listOf("offline-place"), postDoc.stringArray("placeIds"))
        assertNotNull(serverDoc("places/offline-place"))
    }

    // ── 로그인 ──

    @Test
    fun 로그인_전에는_저장이_NotSignedInException으로_알려진다() = runBlocking<Unit> {
        auth.signOut() // 설치 직후 오프라인이라 로그인이 안 된 상태
        val notSignedIn = FirestoreSavedPostRepository(firestore, AuthSession(auth, scope), thumbnails, scope)

        try {
            notSignedIn.save(post("lost"))
            fail("로그인 전 저장은 예외로 알려야 한다")
        } catch (e: NotSignedInException) {
            // 기대한 동작: 크래시나 조용한 유실이 아니라 호출한 쪽이 알 수 있다.
        }
        assertNull(notSignedIn.findByShortcode("x"))
        assertTrue(notSignedIn.cachedPostIds().isEmpty())
        assertTrue(notSignedIn.loadCachedUnresolved().isEmpty())
        assertTrue("로그인 전 목록은 비어 있다", notSignedIn.observeUnresolved().first().isEmpty())
    }

    @Test
    fun 위젯과_알림용_읽기는_캐시에서만_읽고_서버를_부르지_않는다() = runBlocking {
        places.save(place("A"))
        folders.findOrCreateByName("폴더")
        posts.save(post("p1", status = ResolveStatus.UNRESOLVED))
        val deadline = System.currentTimeMillis() + TIMEOUT
        while (System.currentTimeMillis() < deadline && !(places.loadCachedAll().size == 1 && posts.loadCachedUnresolved().size == 1)) delay(50)
        assertEquals("캐시 장소 수", 1, places.loadCachedAll().size)
        assertEquals("캐시 미확정 게시물 수", 1, posts.loadCachedUnresolved().size)
        firestore.disableNetwork().await() // 서버를 부를 수 없는 상태에서도

        assertEquals(listOf("A"), places.loadCachedAll().map { it.id })
        assertEquals(listOf("폴더"), folders.loadCachedAll().map { it.name })
        assertEquals(1, posts.loadCachedUnresolved().size)
        assertEquals(setOf("p1"), posts.cachedPostIds())
    }

    // ── 공유 수신(메인 스레드 runBlocking) ──

    /**
     * ShareReceiverActivity는 메인 스레드에서 runBlocking으로 저장하고 finish()한다. 여기서 한 번이라도 서버나 메인 스레드
     * 콜백을 기다리면 ANR이 난다. 오프라인에서도 제한 시간 안에 끝나는지, 막히면 메인 스레드가 어디서 멈췄는지 확인한다.
     */
    @Test
    fun 메인_스레드에서_공유_저장_경로가_오프라인에서도_막히지_않는다() {
        firestore.disableNetwork().let { runBlocking { it.await() } }
        val main = android.os.Looper.getMainLooper().thread
        var failure: Throwable? = null
        var stackWhenStuck: String? = null
        val watchdog = Thread {
            Thread.sleep(4_000)
            stackWhenStuck = main.stackTrace.joinToString("\n") { "    at $it" }
        }.also { it.start() }

        val progress = mutableListOf<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            try {
                runBlocking {
                    withTimeout(3_000) { assertNull(posts.findByShortcode("MAIN-1")) }
                    progress += "1 findByShortcode 끝"
                    withTimeout(3_000) { posts.save(post("main-thread-post", shortcode = "MAIN-1")) }
                    progress += "2 save 끝"
                    withTimeout(3_000) { assertEquals("main-thread-post", posts.findByShortcode("MAIN-1")?.id) }
                    progress += "3 findByShortcode(저장 후) 끝"
                }
            } catch (t: Throwable) {
                failure = t
            }
        }
        watchdog.join()

        if (failure != null) fail("메인 스레드에서 막혔다: $failure (진행: $progress)\n4초 시점 메인 스레드 스택:\n$stackWhenStuck")
    }

    // ── 로그인 ──

    @Test
    fun 로그인_전에_동시에_여러_번_불러도_익명_사용자는_하나만_만들어진다() = runBlocking {
        auth.signOut()
        val seen = java.util.Collections.synchronizedSet(HashSet<String>())
        val listener = FirebaseAuth.AuthStateListener { it.currentUser?.uid?.let(seen::add) }
        auth.addAuthStateListener(listener)
        try {
            assertFalse(session.isSignedIn())

            val results = (1..5).map { async(Dispatchers.Default) { session.signInIfNeeded() } }
                .map { it.await() }

            assertTrue(results.all { it })
            assertTrue(session.isSignedIn())
            // 인증 상태 리스너는 메인 스레드로 비동기로 오므로 도착할 때까지 잠깐 기다린다.
            awaitUntil { seen.isNotEmpty() }
            assertEquals("앱 시작 루프와 Worker가 겹쳐도 uid는 하나다", 1, seen.size)
        } finally {
            auth.removeAuthStateListener(listener)
        }
    }

    // ── 썸네일 고아 정리의 서버 확인 ──

    @Test
    fun 서버_확인은_서버에_없다고_확인된_id만_돌려주고_오프라인에서는_아무것도_확인하지_못한다() = runBlocking {
        posts.save(post("exists-1"))
        posts.save(post("exists-2"))
        firestore.waitForPendingWrites().await()

        assertEquals(
            setOf("missing-1", "missing-2"),
            posts.findIdsAbsentOnServer(listOf("exists-1", "missing-1", "exists-2", "missing-2")),
        )
        // whereIn은 30개까지라 그보다 많으면 쪼개서 묻는다.
        val many = (1..65).map { "m-$it" } + "exists-1"
        assertEquals((1..65).map { "m-$it" }.toSet(), posts.findIdsAbsentOnServer(many))

        firestore.disableNetwork().await()
        assertEquals("서버가 대답하지 못하면 없다고 확인된 게 아니다", emptySet<String>(), posts.findIdsAbsentOnServer(listOf("exists-1", "missing-1")))
        assertEquals(emptySet<String>(), posts.findIdsAbsentOnServer(emptyList()))
    }

    @Test
    fun 썸네일_정리는_캐시에_없어도_서버에_있으면_지키고_서버에도_없을_때만_지우며_오프라인에선_지우지_않는다() = runBlocking {
        // 캐시엔 이 게시물만 있다.
        posts.save(post("cached"))
        firestore.waitForPendingWrites().await()
        // 서버에만 있는 게시물(캐시 정리·부분 동기화로 캐시에서 빠진 경우를 REST로 흉내낸다).
        createServerOnlyPost("server-only")
        assertFalse("캐시에는 없어야 한다", "server-only" in posts.cachedPostIds())

        fun thumb(key: String) = File(dir, "$key.jpg").apply { parentFile!!.mkdirs(); writeBytes(ByteArray(10)) }
        val maintenance = com.github.chsssssss.eonje.data.local.ThumbnailMaintenance(
            thumbnails, posts,
            object : com.github.chsssssss.eonje.data.local.ThumbnailMaintenanceState {
                override suspend fun isCompactionDone() = true
                override suspend fun markCompactionDone() = Unit
            },
            object : com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus {
                override suspend fun isComplete() = true
                override suspend fun isLegacyDuplicate(shortcode: String) = false
            },
        )

        // 오프라인: 서버가 대답하지 못하므로 의심 파일은 하나도 지우지 않는다.
        val gone = thumb("gone"); val serverOnly = thumb("server-only"); val cached = thumb("cached")
        firestore.disableNetwork().await()
        maintenance.run(System.currentTimeMillis())
        assertTrue("오프라인에선 지우지 않는다", gone.exists() && serverOnly.exists() && cached.exists())

        // 온라인: 서버에도 없는 것만 지운다.
        firestore.enableNetwork().await()
        maintenance.run(System.currentTimeMillis())
        assertFalse("서버에도 없는 파일은 지운다", gone.exists())
        assertTrue("서버에 있는 게시물의 파일은 캐시에 없어도 지키고, 캐시에 있는 것도 그대로", serverOnly.exists() && cached.exists())
    }

    // ── 도구 ──

    private suspend fun awaitPlaces(timeoutMillis: Long = TIMEOUT, condition: (List<Place>) -> Boolean): List<Place> =
        withTimeout(timeoutMillis) { places.observeAll().first { runCatching { condition(it) }.getOrDefault(false) } }

    private suspend fun awaitFolders(condition: (List<com.github.chsssssss.eonje.domain.model.Folder>) -> Boolean) =
        withTimeout(TIMEOUT) { folders.observeAll().first { runCatching { condition(it) }.getOrDefault(false) } }

    private suspend fun awaitUntil(timeoutMillis: Long = TIMEOUT, condition: suspend () -> Boolean) {
        withTimeout(timeoutMillis) { while (!runCatching { condition() }.getOrDefault(false)) delay(50) }
    }

    private fun post(
        id: String,
        status: ResolveStatus = ResolveStatus.PENDING,
        createdAt: Long = 1_000L,
        extractedCount: Int = 0,
        shortcode: String? = "sc-$id",
        caption: String? = null,
        thumbnailUrl: String? = null,
    ) = SavedPost(
        id = id,
        shortcode = shortcode,
        instagramUrl = "https://www.instagram.com/p/$id/",
        caption = caption,
        thumbnailUrl = thumbnailUrl,
        status = status,
        extractedCount = extractedCount,
        createdAt = createdAt,
    )

    private fun place(
        id: String,
        folderId: String? = null,
        kakaoPlaceId: String? = "kakao-$id",
        createdAt: Long = 1_000L,
    ) = Place(
        id = id,
        name = "장소 $id",
        address = "서울 성동구 성수동 1",
        latitude = 37.54,
        longitude = 127.05,
        kakaoPlaceId = kakaoPlaceId,
        category = "카페",
        status = ResolveStatus.RESOLVED,
        createdAt = createdAt,
        resolvedAt = createdAt,
        folderId = folderId,
    )

    private fun candidate(postId: String, index: Int, rank: Int, name: String) = ExtractedCandidate(
        postId = postId,
        extractionIndex = index,
        extractedName = "추출 $index",
        rank = rank,
        place = PlaceCandidate("kakao-$name", name, "주소 $name", "음식점", 37.0 + index, 127.0 + rank),
    )

    /** 서버(에뮬레이터)에 실제로 저장된 문서를 REST로 읽는다. 없으면 null. 에뮬레이터는 "Bearer owner"를 관리자로 받아 규칙을 건너뛴다. */
    private fun serverDoc(path: String): JSONObject? {
        val url = URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents/users/$uid/$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer owner")
        return try {
            if (connection.responseCode == 404) null else JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    /** REST로 게시물 문서를 서버에만 만든다(로컬 캐시를 거치지 않는다). */
    private fun createServerOnlyPost(id: String) {
        val url = URL("http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents/users/$uid/posts?documentId=$id")
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer owner")
        connection.setRequestProperty("Content-Type", "application/json")
        try {
            connection.outputStream.use {
                it.write("""{"fields":{"instagramUrl":{"stringValue":"https://www.instagram.com/p/$id/"},"status":{"stringValue":"RESOLVED"}}}""".toByteArray())
            }
            check(connection.responseCode in 200..299) { "서버 문서 만들기 실패: ${connection.responseCode}" }
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.fields(): JSONObject = getJSONObject("fields")
    private fun JSONObject.hasField(name: String) = fields().has(name)
    private fun JSONObject.opt(name: String): Any? = fields().optJSONObject(name)?.let { it.keys().next().let { type -> it.opt(type) } }
    private fun JSONObject.longField(name: String): Long = fields().getJSONObject(name).getString("integerValue").toLong()
    private fun JSONObject.stringField(name: String): String = fields().getJSONObject(name).getString("stringValue")
    private fun JSONObject.stringArray(name: String): List<String> {
        val values = fields().getJSONObject(name).optJSONObject("arrayValue")?.optJSONArray("values") ?: return emptyList()
        return (0 until values.length()).map { values.getJSONObject(it).getString("stringValue") }
    }

    private fun emulatorReachable(): Boolean = listOf(FIRESTORE_PORT, AUTH_PORT).all { port ->
        runCatching { Socket().use { it.connect(InetSocketAddress(HOST, port), 1_000); true } }.getOrDefault(false)
    }

    private companion object {
        val shared = FirebaseEmulatorEnv
        const val HOST = FirebaseEmulatorEnv.HOST
        const val FIRESTORE_PORT = FirebaseEmulatorEnv.FIRESTORE_PORT
        const val AUTH_PORT = FirebaseEmulatorEnv.AUTH_PORT
        val PROJECT = FirebaseEmulatorEnv.PROJECT
        const val TIMEOUT = 10_000L
        const val OFFLINE_WRITE_TIMEOUT = 5_000L
    }
}
