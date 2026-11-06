package com.github.chsssssss.eonje.data.migration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.firestore.FirebaseEmulatorEnv
import com.github.chsssssss.eonje.data.firestore.FirestoreExtractedCandidateRepository
import com.github.chsssssss.eonje.data.firestore.FirestorePlaceRepository
import com.github.chsssssss.eonje.data.firestore.FirestoreSavedPostRepository
import com.github.chsssssss.eonje.data.firestore.AuthSession
import com.github.chsssssss.eonje.data.firestore.folders
import com.github.chsssssss.eonje.data.firestore.places
import com.github.chsssssss.eonje.data.firestore.posts
import com.github.chsssssss.eonje.data.local.AndroidImageCodec
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRef
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.data.mapper.toDomain
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Room → Firestore 이전을 Firebase Emulator Suite(`scripts/start-emulators.sh`)에 붙여 확인한다. Room은 메모리 DB에 시드한다.
 * 에뮬레이터가 안 떠 있으면 건너뛴다. 테스트마다 새 익명 사용자(uid)를 쓴다.
 */
@RunWith(AndroidJUnit4::class)
class RoomToFirestoreMigratorTest {
    private val env = FirebaseEmulatorEnv
    private val firestore get() = env.firestore
    private val auth get() = env.auth

    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var db: EonjeDatabase
    private lateinit var store: RoomMigrationStateStore
    private lateinit var uid: String
    private var notified = 0

    @Before
    fun setUp() = runBlocking<Unit> {
        assumeTrue("Firebase Emulator Suite가 호스트에서 실행 중이어야 한다", env.reachable())
        firestore.enableNetwork().await()
        auth.signOut()
        uid = auth.signInAnonymously().await().user!!.uid

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = File(context.cacheDir, "migration-test-${System.nanoTime()}").apply { mkdirs() }
        db = Room.inMemoryDatabaseBuilder(context, EonjeDatabase::class.java).allowMainThreadQueries().build()
        store = RoomMigrationStateStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "m.preferences_pb") }))
        notified = 0
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.close()
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
        if (env.isStarted) runCatching { runBlocking { firestore.enableNetwork().await() } }
    }

    // ── 데이터 범위와 관계 ──

    @Test
    fun 폴더_장소_게시물이_개수와_관계_그대로_옮겨지고_memo는_없다() = runBlocking {
        seed()

        val outcome = migrator().migrate(uid)

        assertEquals(MigrationOutcome.Migrated(folders = 2, places = 5, posts = 5, durationMillis = (outcome as MigrationOutcome.Migrated).durationMillis), outcome)
        assertTrue("위젯 갱신 알림", notified > 0)

        // 문서 ID는 Room의 id 그대로이고, 개수가 같다.
        assertEquals(setOf("folder-1", "folder-2"), serverIds(firestore.folders(uid)))
        assertEquals((1..5).map { "place-$it" }.toSet(), serverIds(firestore.places(uid)))
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))

        // 폴더 관계: 장소 1·2는 folder-1, 3은 folder-2, 4·5는 미분류.
        val places = firestore.places(uid).get(Source.SERVER).await().documents.associateBy { it.id }
        assertEquals("folder-1", places.getValue("place-1").getString("folderId"))
        assertEquals("folder-1", places.getValue("place-2").getString("folderId"))
        assertEquals("folder-2", places.getValue("place-3").getString("folderId"))
        assertNull(places.getValue("place-4").getString("folderId"))
        assertNull(places.getValue("place-5").getString("folderId"))

        // memo와 썸네일 경로는 문서에 없다.
        places.values.forEach { assertFalse("memo가 옮겨졌다: ${it.id}", it.data!!.containsKey("memo")) }
        firestore.posts(uid).get(Source.SERVER).await().documents.forEach {
            assertFalse("thumbnailUrl이 옮겨졌다: ${it.id}", it.data!!.containsKey("thumbnailUrl"))
        }

        // 게시물-장소 연결과 후보.
        val posts = firestore.posts(uid).get(Source.SERVER).await().documents.associateBy { it.id }
        assertEquals(listOf("place-1", "place-2"), (posts.getValue("post-1").get("placeIds") as List<*>).filterIsInstance<String>().sorted())
        assertEquals(listOf("place-3"), posts.getValue("post-2").get("placeIds"))
        assertEquals(3, (posts.getValue("post-3").get("candidates") as List<*>).size)

        // Firestore 저장소가 읽은 도메인 값이 Room의 도메인 값과 같다(같은 매퍼를 쓰는지 확인).
        val session = signedInSession()
        val thumbs = ThumbnailStore(File(dir, "thumbs"), OkHttpClient(), AndroidImageCodec)
        val placeRepo = FirestorePlaceRepository(firestore, session, thumbs, PlacesChangedNotifier { }, scope)
        val postRepo = FirestoreSavedPostRepository(firestore, session, thumbs, scope)
        val candRepo = FirestoreExtractedCandidateRepository(firestore, session)
        val roomPlaces = db.placeDao().pageAfter("", 100).map { it.toDomain() }.associateBy { it.id }
        assertEquals(roomPlaces, placeRepo.observeAll().first { it.size == 5 }.associateBy { it.id })
        val roomPosts = db.savedPostDao().pageAfter("", 100).map { it.toDomain().copy(thumbnailUrl = null) }
        assertEquals(roomPosts.sortedBy { it.id }, postRepo.findByIds((1..5).map { "post-$it" }).sortedBy { it.id })
        assertEquals(
            db.extractedCandidateDao().forPosts(listOf("post-3")).map { it.toDomain() },
            candRepo.observeForPost("post-3").first { it.isNotEmpty() },
        )

        // 완료 표시는 uid와 함께 저장되고, Room 데이터는 그대로다(지우지 않는다).
        val state = store.read()
        assertTrue(state.completed)
        assertEquals(uid, state.uid)
        assertEquals(2, db.folderDao().count())
        assertEquals(5, db.placeDao().count())
        assertEquals(5, db.savedPostDao().count())
        assertEquals("Room의 memo도 그대로", "비밀 메모", db.placeDao().findById("place-1")!!.memo)
    }

    @Test
    fun 이전한_문서와_새로_만든_문서의_필드_모양이_같다() = runBlocking {
        seed()
        migrator().migrate(uid)
        val session = signedInSession()
        val thumbs = ThumbnailStore(File(dir, "thumbs"), OkHttpClient(), AndroidImageCodec)
        val postRepo = FirestoreSavedPostRepository(firestore, session, thumbs, scope)
        val placeRepo = FirestorePlaceRepository(firestore, session, thumbs, PlacesChangedNotifier { }, scope)
        val candRepo = FirestoreExtractedCandidateRepository(firestore, session)

        // 새로 만든 게시물: 저장 → 장소 연결(→ post-1과 같은 모양), 저장 → 후보 저장(→ post-3과 같은 모양)
        placeRepo.save(Place("new-place", "새 가게", "주소", 1.0, 2.0, "k-new", null, ResolveStatus.RESOLVED, 1L, 1L, null))
        postRepo.save(newPost("new-linked"))
        placeRepo.linkPostToPlace("new-linked", "new-place")
        postRepo.save(newPost("new-candidates"))
        candRepo.replaceForPost("new-candidates", db.extractedCandidateDao().forPosts(listOf("post-3")).map { it.toDomain().copy(postId = "new-candidates") })

        fun keys(col: CollectionReference, id: String) = runBlocking { col.document(id).get(Source.SERVER).await().data!!.keys }
        assertEquals(keys(firestore.posts(uid), "post-1"), keys(firestore.posts(uid), "new-linked"))
        assertEquals(keys(firestore.posts(uid), "post-3"), keys(firestore.posts(uid), "new-candidates"))
        assertEquals(keys(firestore.places(uid), "place-1"), keys(firestore.places(uid), "new-place"))
    }

    @Test
    fun 이전_전에_새로_저장한_데이터와_섞여도_둘_다_남는다() = runBlocking {
        seed()
        val session = signedInSession()
        val thumbs = ThumbnailStore(File(dir, "thumbs"), OkHttpClient(), AndroidImageCodec)
        FirestoreSavedPostRepository(firestore, session, thumbs, scope).save(newPost("brand-new"))
        FirestorePlaceRepository(firestore, session, thumbs, PlacesChangedNotifier { }, scope)
            .save(Place("brand-new-place", "새 가게", "주소", 1.0, 2.0, "k-brand", null, ResolveStatus.RESOLVED, 1L, 1L, null))

        migrator().migrate(uid)

        assertEquals(6, serverIds(firestore.posts(uid)).size)
        assertEquals(6, serverIds(firestore.places(uid)).size)
        assertTrue("brand-new" in serverIds(firestore.posts(uid)))
        assertTrue("brand-new-place" in serverIds(firestore.places(uid)))
    }

    // ── 이어하기와 덮어쓰기 방지 ──

    @Test
    fun 커밋_직후_체크포인트_저장_전에_끊겨도_이어가며_사용자가_고친_문서를_덮어쓰지_않는다() = runBlocking {
        seed()
        // 장소 5개를 2개씩: [1,2] [3,4] [5]. 두 번째 배치를 커밋한 직후(체크포인트 저장 전)에 프로세스가 죽는 상황.
        val crash = object : MigrationHooks {
            override suspend fun afterCommit(kind: MigrationKind, page: Int) {
                if (kind == MigrationKind.PLACES && page == 1) throw Crash()
            }
        }
        assertThrows(Crash::class.java) { runBlocking { migrator(batchSize = 2, hooks = crash).migrate(uid) } }
        val mid = store.read()
        assertFalse(mid.completed)
        assertTrue(mid.foldersDone)
        assertEquals("두 번째 배치는 커밋됐지만 체크포인트엔 아직 없다", "place-2", mid.placeCursor)
        assertEquals(setOf("place-1", "place-2", "place-3", "place-4"), serverIds(firestore.places(uid)))

        // 그 사이 사용자가 Firestore 쪽에서 방금 커밋된 문서를 고쳤다.
        firestore.places(uid).document("place-3").update("name", "사용자가 고친 이름").await()

        migrator(batchSize = 2).migrate(uid)

        val places = firestore.places(uid).get(Source.SERVER).await().documents.associateBy { it.id }
        assertEquals(5, places.size)
        assertEquals("Room의 옛 값으로 덮어쓰지 않는다", "사용자가 고친 이름", places.getValue("place-3").getString("name"))
        assertEquals("고치지 않은 문서는 Room 값 그대로", db.placeDao().findById("place-4")!!.name, places.getValue("place-4").getString("name"))
        assertEquals(db.placeDao().findById("place-5")!!.name, places.getValue("place-5").getString("name"))
        assertEquals(5, serverIds(firestore.posts(uid)).size)
        assertTrue(store.read().completed)
    }

    @Test
    fun 체크포인트_저장_뒤에_끊겨도_이미_옮겨진_게시물은_그대로이고_나머지만_이어서_옮긴다() = runBlocking {
        seed()
        val crash = object : MigrationHooks {
            override suspend fun afterCheckpoint(kind: MigrationKind, page: Int) {
                if (kind == MigrationKind.POSTS && page == 0) throw Crash()
            }
        }
        assertThrows(Crash::class.java) { runBlocking { migrator(batchSize = 2, hooks = crash).migrate(uid) } }
        assertEquals("post-2", store.read().postCursor)
        assertEquals(setOf("post-1", "post-2"), serverIds(firestore.posts(uid)))
        firestore.posts(uid).document("post-1").update("caption", "사용자가 고침").await()

        migrator(batchSize = 2).migrate(uid)

        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))
        assertEquals("사용자가 고침", firestore.posts(uid).document("post-1").get(Source.SERVER).await().getString("caption"))
        val state = store.read()
        assertEquals("처리 개수는 이어하기 전 것까지 합친다", 5, state.postCount)
        assertEquals(2, state.folderCount)
        assertEquals(5, state.placeCount)
    }

    @Test
    fun 서버_확정을_못_받으면_체크포인트를_저장하지_않고_온라인이_되면_이어간다() = runBlocking {
        seed()
        firestore.disableNetwork().await()

        assertThrows(Exception::class.java) { runBlocking { migrator().migrate(uid) } }

        val after = store.read()
        assertFalse(after.completed)
        assertFalse(after.foldersDone)
        assertEquals("", after.folderCursor)
        assertEquals("Room은 그대로", 5, db.placeDao().count())

        firestore.enableNetwork().await()
        migrator().migrate(uid)
        assertEquals(5, serverIds(firestore.places(uid)).size)
        assertTrue(store.read().completed)
    }

    @Test
    fun 이전_도중_사용자가_지운_폴더를_가리키던_장소는_미분류로_옮겨진다() = runBlocking {
        seed()
        // 폴더 이전이 끝난 직후, 사용자가 이미 옮겨진 folder-1을 지운다(아직 안 옮겨진 장소는 그걸 모른다).
        val deleteFolder = object : MigrationHooks {
            override suspend fun afterCheckpoint(kind: MigrationKind, page: Int) {
                if (kind == MigrationKind.FOLDERS && page == 0) firestore.folders(uid).document("folder-1").delete().await()
            }
        }

        migrator(hooks = deleteFolder).migrate(uid)

        val places = firestore.places(uid).get(Source.SERVER).await().documents.associateBy { it.id }
        assertNull(places.getValue("place-1").getString("folderId"))
        assertNull(places.getValue("place-2").getString("folderId"))
        assertEquals("지워지지 않은 폴더는 그대로", "folder-2", places.getValue("place-3").getString("folderId"))
    }

    @Test
    fun Room에_없는_폴더를_가리키는_folderId는_미분류로_옮겨진다() = runBlocking {
        db.placeDao().insert(placeEntity("lonely", folderId = "no-such-folder"))
        db.folderDao().insert(FolderEntity("folder-1", "폴더", 1L, 0xFF1E88E5.toInt(), "☕"))

        migrator().migrate(uid)

        assertNull(firestore.places(uid).document("lonely").get(Source.SERVER).await().getString("folderId"))
    }

    // ── 완료 표시와 uid ──

    @Test
    fun 같은_uid로_두_번_실행하면_두번째는_아무것도_쓰지_않는다() = runBlocking {
        seed()
        var started = 0
        migrator().migrate(uid) { started++ }
        firestore.places(uid).document("place-1").update("name", "고친 이름").await()

        val second = migrator().migrate(uid) { started++ }

        assertEquals(MigrationOutcome.AlreadyCompleted, second)
        assertEquals(1, started)
        assertEquals("고친 이름", firestore.places(uid).document("place-1").get(Source.SERVER).await().getString("name"))
    }

    @Test
    fun uid가_바뀌면_새_uid로_처음부터_다시_옮긴다() = runBlocking {
        seed()
        val first = uid
        migrator().migrate(first)
        assertEquals(first, store.read().uid)
        assertEquals(5, serverIds(firestore.posts(first)).size)

        // 자동 백업이 완료 기록을 복원했지만 새 기기에선 다른 uid로 로그인된 상황.
        auth.signOut()
        val second = auth.signInAnonymously().await().user!!.uid
        var started = 0
        val outcome = migrator().migrate(second) { started++ }

        assertTrue(outcome is MigrationOutcome.Migrated)
        assertEquals(1, started)
        assertEquals(second, store.read().uid)
        assertTrue(store.read().completed)
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(second)))
        // (보안 규칙상 지금 로그인한 uid로는 이전 uid의 데이터를 읽을 수 없다 — 새 uid 경로에만 썼다는 건 위 단언으로 충분하다)
    }

    @Test
    fun Room이_비어_있으면_옮기지_않고_바로_완료로_표시한다() = runBlocking {
        var started = 0

        val outcome = migrator().migrate(uid) { started++ }

        assertEquals(MigrationOutcome.NothingToMigrate, outcome)
        assertEquals(0, started)
        val state = store.read()
        assertTrue(state.completed)
        assertEquals(uid, state.uid)
        assertTrue(serverIds(firestore.posts(uid)).isEmpty())
        assertTrue(serverIds(firestore.places(uid)).isEmpty())
        assertEquals(MigrationOutcome.AlreadyCompleted, migrator().migrate(uid))
    }

    // ── 도구 ──

    private class Crash : RuntimeException("강제 종료 시뮬레이션")

    private fun migrator(batchSize: Int = 499, hooks: MigrationHooks = MigrationHooks.None) = RoomToFirestoreMigrator(
        firestore, db.folderDao(), db.placeDao(), db.savedPostDao(), db.postPlaceCrossRefDao(), db.extractedCandidateDao(),
        store, PlacesChangedNotifier { notified++ }, batchSize, hooks, System::currentTimeMillis,
    )

    private fun signedInSession() = AuthSession(auth, scope)

    private suspend fun serverIds(col: CollectionReference): Set<String> =
        col.get(Source.SERVER).await().documents.map { it.id }.toSet()

    private fun placeEntity(id: String, folderId: String?) = PlaceEntity(
        id = id, name = "가게 $id", address = "서울 성동구 $id", latitude = 37.5, longitude = 127.0, kakaoPlaceId = "k-$id",
        category = "음식점 > 한식", memo = "비밀 메모", status = ResolveStatus.RESOLVED, createdAt = 100L, resolvedAt = 200L, folderId = folderId,
    )

    private fun newPost(id: String) = SavedPost(
        id = id, shortcode = "sc-$id", instagramUrl = "https://www.instagram.com/p/sc-$id/", caption = null, thumbnailUrl = null,
        status = ResolveStatus.PENDING, extractedCount = 0, createdAt = 1L,
    )

    /** 폴더 2, 장소 5(폴더 연결·memo 포함), 게시물 5(연결·후보·썸네일 경로 포함). id는 정렬 순서가 눈에 보이게 짓는다. */
    private suspend fun seed() {
        db.folderDao().insert(FolderEntity("folder-1", "성수 맛집", 10L, 0xFF1E88E5.toInt(), "☕"))
        db.folderDao().insert(FolderEntity("folder-2", "회사 근처", 20L, 0xFFE53935.toInt(), "🍽️"))
        (1..5).forEach { n -> db.placeDao().insert(placeEntity("place-$n", folderId = when (n) { 1, 2 -> "folder-1"; 3 -> "folder-2"; else -> null })) }
        fun post(n: Int, status: ResolveStatus, reason: UnresolvedReason? = null, count: Int = 0) = SavedPostEntity(
            id = "post-$n", shortcode = "SC$n", instagramUrl = "https://www.instagram.com/p/SC$n/", caption = "캡션 $n",
            thumbnailUrl = "file:///data/user/0/x/files/thumbnails/post-$n.jpg", status = status, extractedCount = count,
            createdAt = 1000L + n, unresolvedReason = reason,
        )
        db.savedPostDao().insert(post(1, ResolveStatus.RESOLVED, count = 1))
        db.savedPostDao().insert(post(2, ResolveStatus.RESOLVED, count = 1))
        db.savedPostDao().insert(post(3, ResolveStatus.NEEDS_REVIEW, count = 2))
        db.savedPostDao().insert(post(4, ResolveStatus.UNRESOLVED, UnresolvedReason.PLACE_NOT_FOUND))
        db.savedPostDao().insert(post(5, ResolveStatus.PENDING))
        db.postPlaceCrossRefDao().insert(PostPlaceCrossRef("post-1", "place-1"))
        db.postPlaceCrossRefDao().insert(PostPlaceCrossRef("post-1", "place-2"))
        db.postPlaceCrossRefDao().insert(PostPlaceCrossRef("post-2", "place-3"))
        db.extractedCandidateDao().insertAll(
            listOf(
                ExtractedCandidateEntity("post-3", 0, "성수 칼국수", 0, "k-a", "성수칼국수 본점", "서울 성동구 1", "음식점 > 한식", 37.1, 127.1),
                ExtractedCandidateEntity("post-3", 0, "성수 칼국수", 1, "k-b", "성수칼국수 2호점", "서울 성동구 2", null, 37.2, 127.2),
                ExtractedCandidateEntity("post-3", 1, "연남 파스타", 0, "k-c", "연남파스타", "서울 마포구 3", "음식점 > 양식", 37.3, 127.3),
            ),
        )
    }
}
