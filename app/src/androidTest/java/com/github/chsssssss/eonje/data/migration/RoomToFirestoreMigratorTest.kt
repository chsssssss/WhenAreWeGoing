package com.github.chsssssss.eonje.data.migration

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.firestore.FirebaseEmulatorEnv
import com.github.chsssssss.eonje.data.firestore.FirestoreExtractedCandidateRepository
import com.github.chsssssss.eonje.data.firestore.FirestorePlaceRepository
import com.github.chsssssss.eonje.data.firestore.placeUpdate
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
    private val SECRET = "SECRET_CAPTION_"

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
        // 장소 문서의 postIds는 Room의 연결(crossref)에서 채워진다 — Room 도메인 값엔 없으니 같은 연결로 채워서 비교한다.
        val refsByPlace = db.postPlaceCrossRefDao().forPlaces((1..5).map { "place-$it" }).groupBy({ it.placeId }, { it.postId })
        val roomPlaces = db.placeDao().pageAfter("", 100).map { it.toDomain() }
            .map { it.copy(postIds = refsByPlace[it.id].orEmpty().sorted()) }.associateBy { it.id }
        assertEquals(listOf("post-1"), roomPlaces.getValue("place-1").postIds)
        assertEquals(listOf("post-2"), roomPlaces.getValue("place-3").postIds)
        assertEquals(roomPlaces, placeRepo.observeAll().first { it.size == 5 }.associateBy { it.id }
            .mapValues { it.value.copy(postIds = it.value.postIds.sorted()) })
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
        firestore.places(uid).document("place-3").update(placeUpdate("name" to "사용자가 고친 이름")).await()

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

    // ── 문제 문서 격리: 규칙이 거부하는 문서가 섞여 있어도 이전은 끝난다 ──

    @Test
    fun 규칙에_걸리는_게시물_하나가_섞여도_나머지는_전부_이전되고_완료로_끝나며_걸린_문서만_건너뛴다() = runBlocking {
        seed()
        db.savedPostDao().insert(unfixablePost("post-bad")) // extractedCount 5000 → 규칙 상한(1000) 초과, 고칠 수 없다

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals("정상 게시물 5개만 이전한 개수로 센다", 5, outcome.posts)
        assertEquals(1, outcome.skipped)
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))
        val state = store.read()
        assertTrue("건너뛴 문서가 있어도 완료로 표시한다(홈 배너가 사라진다)", state.completed)
        assertEquals(setOf("POSTS:post-bad:PERMISSION_DENIED"), state.skipped)
        assertEquals(setOf("post-bad"), state.skippedPostIds)
        // 캡션 같은 내용은 기록하지 않는다. ID와 사유 코드뿐이다.
        assertFalse(state.skipped.any { it.contains(SECRET) })
        // Room 데이터는 그대로 — 건너뛴 문서도 남아 있다.
        assertEquals(6, db.savedPostDao().count())
        assertTrue(db.savedPostDao().findById("post-bad")!!.caption!!.startsWith(SECRET))
        // 거부 원인 감별용 프로브 문서는 지워졌다.
        assertFalse("migration-probe" in serverIds(firestore.posts(uid)))
        assertEquals(MigrationOutcome.AlreadyCompleted, migrator().migrate(uid))
    }

    @Test
    fun 한_배치_안에_고칠_수_없는_문서가_여러_개여도_모두_격리하고_나머지를_옮긴다() = runBlocking<Unit> {
        seed()
        db.placeDao().insert(placeEntity("place-bad", folderId = null).copy(latitude = 91.0)) // 좌표 범위 밖 — 고칠 수 없다
        db.savedPostDao().insert(unfixablePost("post-b1"))
        db.savedPostDao().insert(unfixablePost("post-b2").copy(instagramUrl = "https://www.instagram.com/p/" + "a".repeat(500))) // 정규형도 500자 초과
        db.savedPostDao().insert(unfixablePost("post-b3").copy(shortcode = "s".repeat(101))) // 식별자는 자르지 않는다

        // 배치 크기 4: 게시물 두 번째 페이지는 [post-5, post-b1, post-b2, post-b3] — 4개 중 3개가 걸린다.
        val outcome = migrator(batchSize = 4).migrate(uid) as MigrationOutcome.Migrated

        assertEquals(4, outcome.skipped)
        assertEquals(0, outcome.sanitized)
        assertEquals(2, outcome.folders)
        assertEquals(5, outcome.places)
        assertEquals(5, outcome.posts)
        assertEquals(setOf("folder-1", "folder-2"), serverIds(firestore.folders(uid)))
        assertEquals((1..5).map { "place-$it" }.toSet(), serverIds(firestore.places(uid)))
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))
        assertEquals(
            setOf(
                "PLACES:place-bad:PERMISSION_DENIED",
                "POSTS:post-b1:PERMISSION_DENIED", "POSTS:post-b2:PERMISSION_DENIED", "POSTS:post-b3:PERMISSION_DENIED",
            ),
            store.read().skipped,
        )
        assertTrue(store.read().completed)
        assertEquals("Room은 그대로", 6, db.placeDao().count())
        assertEquals(8, db.savedPostDao().count())
    }

    // ── 규칙 상한을 넘는 값 중 안전하게 고칠 수 있는 것은 고쳐서 옮긴다 ──

    @Test
    fun 빈_이름과_101자_이름의_폴더는_이름만_고쳐져서_이전되고_그_폴더의_장소는_그_폴더에_남는다() = runBlocking<Unit> {
        db.folderDao().insert(FolderEntity("folder-blank", "   ", 1L, 0xFF1E88E5.toInt(), "☕"))
        db.folderDao().insert(FolderEntity("folder-empty", "", 2L, 0xFFE53935.toInt(), "🍽️"))
        db.folderDao().insert(FolderEntity("folder-long", "가".repeat(101), 3L, 0xFF43A047.toInt(), "🏠"))
        db.folderDao().insert(FolderEntity("folder-ok", "정상 폴더", 4L, 0xFFFB8C00.toInt(), "⭐"))
        db.placeDao().insert(placeEntity("place-blank", folderId = "folder-blank"))
        db.placeDao().insert(placeEntity("place-empty", folderId = "folder-empty"))
        db.placeDao().insert(placeEntity("place-long", folderId = "folder-long"))
        db.placeDao().insert(placeEntity("place-ok", folderId = "folder-ok"))

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals("건너뛴 게 없다", 0, outcome.skipped)
        assertEquals("고쳐서 옮긴 폴더 3개", 3, outcome.sanitized)
        assertEquals(4, outcome.folders)
        val folders = firestore.folders(uid).get(Source.SERVER).await().documents.associateBy { it.id }
        assertEquals("이름 없는 폴더", folders.getValue("folder-blank").getString("name"))
        assertEquals("이름 없는 폴더", folders.getValue("folder-empty").getString("name"))
        assertEquals("가".repeat(100), folders.getValue("folder-long").getString("name"))
        assertEquals("정상 폴더", folders.getValue("folder-ok").getString("name"))
        // 이름 말고는 그대로: 색과 아이콘, 생성 시각
        assertEquals("🏠", folders.getValue("folder-long").getString("iconKey"))
        assertEquals(0xFF43A047.toInt().toLong(), folders.getValue("folder-long").getLong("color"))
        assertEquals(3L, folders.getValue("folder-long").getLong("createdAt"))
        // 그 폴더의 장소는 미분류가 아니라 그 폴더에 남는다
        val places = firestore.places(uid).get(Source.SERVER).await().documents.associateBy { it.id }
        assertEquals("folder-blank", places.getValue("place-blank").getString("folderId"))
        assertEquals("folder-empty", places.getValue("place-empty").getString("folderId"))
        assertEquals("folder-long", places.getValue("place-long").getString("folderId"))
        assertEquals("folder-ok", places.getValue("place-ok").getString("folderId"))
        // 기록에는 필드 이름만 있고 값(폴더 이름)은 없다
        val state = store.read()
        assertEquals(
            setOf("FOLDERS:folder-blank:name", "FOLDERS:folder-empty:name", "FOLDERS:folder-long:name"),
            state.sanitized,
        )
        assertTrue(state.skipped.isEmpty())
        assertTrue(state.completed)
        // Room의 원본은 그대로다
        assertEquals("가".repeat(101), db.folderDao().findById("folder-long")!!.name)
        assertEquals("   ", db.folderDao().findById("folder-blank")!!.name)
    }

    @Test
    fun 장소_이름_주소_카테고리와_게시물_캡션_URL도_상한까지_고쳐서_옮기고_Room_원본은_그대로다() = runBlocking<Unit> {
        seed()
        val longUrl = "https://www.instagram.com/p/ABC123/?igsh=" + "x".repeat(600) // 500자 초과지만 인스타 게시물 URL로 해석된다
        db.placeDao().insert(
            placeEntity("place-long", folderId = null).copy(name = "가".repeat(201), address = "나".repeat(301), category = "다".repeat(201)),
        )
        db.savedPostDao().insert(
            unfixablePost("post-long").copy(extractedCount = 0, caption = SECRET + "가".repeat(21_000), instagramUrl = longUrl),
        )

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(0, outcome.skipped)
        assertEquals("장소 1개 + 게시물 1개", 2, outcome.sanitized)
        val place = firestore.places(uid).document("place-long").get(Source.SERVER).await()
        assertEquals("가".repeat(200), place.getString("name"))
        assertEquals("나".repeat(300), place.getString("address"))
        assertEquals("다".repeat(200), place.getString("category"))
        val post = firestore.posts(uid).document("post-long").get(Source.SERVER).await()
        assertEquals(20_000, post.getString("caption")!!.length)
        assertTrue(post.getString("caption")!!.startsWith(SECRET))
        assertEquals("https://www.instagram.com/p/ABC123/", post.getString("instagramUrl"))
        assertEquals(
            setOf("PLACES:place-long:name,address,category", "POSTS:post-long:caption,instagramUrl"),
            store.read().sanitized,
        )
        // 값 자체는 기록에 없다
        assertFalse(store.read().sanitized.any { it.contains(SECRET) || it.contains("가가") })
        // Room 원본은 건드리지 않는다
        assertEquals(201, db.placeDao().findById("place-long")!!.name!!.length)
        assertEquals(21_000 + SECRET.length, db.savedPostDao().findById("post-long")!!.caption!!.length)
    }

    @Test
    fun 고친_뒤에도_거부되는_문서는_고쳐서_옮긴_것으로_세지_않고_건너뛴다() = runBlocking<Unit> {
        seed()
        // 캡션은 고칠 수 있지만 extractedCount는 못 고친다 → 끝내 거부
        db.savedPostDao().insert(unfixablePost("post-both").copy(caption = "가".repeat(21_000)))

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(1, outcome.skipped)
        assertEquals(0, outcome.sanitized)
        assertEquals(setOf("POSTS:post-both:PERMISSION_DENIED"), store.read().skipped)
        assertTrue(store.read().sanitized.isEmpty())
        assertFalse("post-both" in serverIds(firestore.posts(uid)))
    }

    @Test
    fun 규칙_상한_경계값_그대로인_문서는_고치지_않고_이전된다() = runBlocking<Unit> {
        db.folderDao().insert(FolderEntity("folder-edge", "가".repeat(100), 1L, 0, "☕"))
        db.placeDao().insert(placeEntity("place-edge", folderId = "folder-edge").copy(name = "가".repeat(200), address = "나".repeat(300), category = "다".repeat(200)))
        db.savedPostDao().insert(unfixablePost("post-edge").copy(extractedCount = 1000, caption = "가".repeat(20_000)))

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(0, outcome.sanitized)
        assertEquals(0, outcome.skipped)
        assertEquals(20_000, firestore.posts(uid).document("post-edge").get(Source.SERVER).await().getString("caption")!!.length)
    }

    // ── 프로브 문서 ──

    @Test
    fun 프로브를_쓴_직후_앱이_죽어도_화면에는_안_보이고_다음_이전_시작에서_지워진다() = runBlocking<Unit> {
        seed()
        db.savedPostDao().insert(unfixablePost("post-bad")) // 고칠 수 없는 문서 → 영구 거부 → 프로브가 돈다
        val crash = object : MigrationHooks {
            override suspend fun afterProbeWritten() = throw Crash()
        }

        assertThrows(Crash::class.java) { runBlocking { migrator(hooks = crash).migrate(uid) } }

        // 프로브 문서가 서버에 남았고, 지웠다는 확인이 없다는 표시도 남았다.
        assertTrue("프로브가 남아 있어야 한다", RoomToFirestoreMigrator.PROBE_ID in serverIds(firestore.posts(uid)))
        assertTrue(store.read().probePending)

        // 남아 있는 동안에도 사용자 화면(인박스·홈·지도·위젯의 데이터원)에는 보이지 않는다.
        val session = signedInSession()
        val thumbs = ThumbnailStore(File(dir, "thumbs"), OkHttpClient(), AndroidImageCodec)
        val postRepo = FirestoreSavedPostRepository(firestore, session, thumbs, scope)
        val placeRepo = FirestorePlaceRepository(firestore, session, thumbs, PlacesChangedNotifier { }, scope)
        assertTrue("인박스·배지(미확정)에 없다", postRepo.observeUnresolved().first().none { it.id == RoomToFirestoreMigrator.PROBE_ID })
        assertTrue("홈·지도·위젯의 장소별 게시물에 없다", placeRepo.observePostsForPlace("any-place").first().none { it.id == RoomToFirestoreMigrator.PROBE_ID })
        assertTrue("장소로는 더더욱 없다", placeRepo.observeAll().first().none { it.id == RoomToFirestoreMigrator.PROBE_ID })
        assertTrue("위젯이 읽는 캐시 목록에도 없다", postRepo.loadCachedUnresolved().none { it.id == RoomToFirestoreMigrator.PROBE_ID })

        // 다음 이전 시작에서 남은 프로브를 먼저 지우고 이어서 끝낸다.
        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(1, outcome.skipped)
        assertFalse("프로브가 지워져야 한다", RoomToFirestoreMigrator.PROBE_ID in serverIds(firestore.posts(uid)))
        assertFalse(store.read().probePending)
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))
        assertTrue(store.read().completed)
    }

    @Test
    fun 프로브가_거부되면_프로브_표시를_남기지_않는다() = runBlocking<Unit> {
        seed()
        val signOut = object : MigrationHooks {
            override suspend fun beforeCommit(kind: MigrationKind, ids: List<String>) {
                if (kind == MigrationKind.FOLDERS) { auth.signOut(); kotlinx.coroutines.delay(2_000) }
            }
        }

        runCatching { migrator(hooks = signOut).migrate(uid) }

        assertFalse("거부됐으니 지울 문서가 없다 — 다음 시작에 불필요한 삭제를 하지 않는다", store.read().probePending)
    }

    @Test
    fun 정상_이전에서는_프로브를_쓰지도_지우지도_않는다() = runBlocking<Unit> {
        seed()
        migrator().migrate(uid)

        assertFalse(RoomToFirestoreMigrator.PROBE_ID in serverIds(firestore.posts(uid)))
        assertFalse(store.read().probePending)
    }

    @Test
    fun 전부_문제_문서여도_계정_전체_거부가_아니면_모두_건너뛰고_완료한다() = runBlocking {
        db.savedPostDao().insert(unfixablePost("post-b1"))
        db.savedPostDao().insert(unfixablePost("post-b2"))

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(0, outcome.posts)
        assertEquals(2, outcome.skipped)
        assertTrue(store.read().completed)
        assertTrue(serverIds(firestore.posts(uid)).isEmpty())
    }

    @Test
    fun 일시적_오류와_영구_실패가_섞이면_일시적_오류는_재시도하고_영구_실패만_격리한다() = runBlocking {
        seed()
        db.savedPostDao().insert(unfixablePost("post-bad"))
        // 게시물 배치: 1차 전체 커밋(거부) → 절반씩 나눠 다시 커밋. 그 두 번째 하위 커밋에서 네트워크 오류가 난다.
        var postCommits = 0
        val flaky = object : MigrationHooks {
            override suspend fun beforeCommit(kind: MigrationKind, ids: List<String>) {
                if (kind == MigrationKind.POSTS && ++postCommits == 3) throw java.io.IOException("네트워크 끊김 시뮬레이션")
            }
        }
        assertThrows(java.io.IOException::class.java) { runBlocking { migrator(hooks = flaky).migrate(uid) } }
        val mid = store.read()
        assertFalse("일시적 오류는 완료도 격리도 아니다", mid.completed)
        assertFalse("끝나지 않은 페이지의 격리 기록은 남기지 않는다", mid.skipped.isNotEmpty())
        assertEquals("게시물 체크포인트는 아직 안 움직였다", "", mid.postCursor)

        // 다시 시도하면 이어서 끝난다: 이미 커밋된 정상 문서는 건너뛰고(중복·덮어쓰기 없음), 걸린 문서만 격리된다.
        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(1, outcome.skipped)
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))
        assertEquals(setOf("POSTS:post-bad:PERMISSION_DENIED"), store.read().skipped)
        assertTrue(store.read().completed)
    }

    @Test
    fun 계정_전체가_거부되면_문서를_건너뛰지_않고_재시도하게_둔다() = runBlocking {
        seed()
        // 규칙 미배포·로그인 풀림·App Check 시행 같은 상황: 첫 커밋 직전에 로그아웃해서 이후의 모든 쓰기가 거부되게 한다.
        val signOut = object : MigrationHooks {
            override suspend fun beforeCommit(kind: MigrationKind, ids: List<String>) {
                if (kind == MigrationKind.FOLDERS) {
                    auth.signOut()
                    // Firestore SDK가 사용자 전환을 처리할 시간을 준다 — 바로 커밋하면 이전 사용자의 쓰기 큐에 들어가 거부도 승인도
                    // 못 받고 멈춘다(실제로 사용자가 로그아웃되는 일은 없고, 이 테스트가 "계정 전체 거부"를 흉내내려는 방법일 뿐이다).
                    kotlinx.coroutines.delay(2_000)
                }
            }
        }

        val failure = runCatching { migrator(hooks = signOut).migrate(uid) }.exceptionOrNull()

        assertTrue("건너뛰고 끝내면 안 되고 실패로 던져야 한다: $failure", failure is com.google.firebase.firestore.FirebaseFirestoreException)
        val state = store.read()
        assertFalse("완료로 표시하면 안 된다", state.completed)
        assertTrue("아무것도 건너뛴 것으로 기록하면 안 된다", state.skipped.isEmpty())
        assertEquals("Room은 그대로", 5, db.savedPostDao().count())
    }

    @Test
    fun Room에서_읽을_수_없는_행은_격리하고_나머지를_옮긴다() = runBlocking {
        seed()
        // 지금 앱이 모르는 status 문자열 — Converters가 valueOf로 읽다 예외를 던져 그 페이지 전체를 못 읽게 만드는 행
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO saved_posts(id, shortcode, instagramUrl, caption, thumbnailUrl, status, extractedCount, createdAt, unresolvedReason) " +
                "VALUES('post-zz', 'SCZ', 'https://www.instagram.com/p/SCZ/', '$SECRET', NULL, 'LEGACY_STATUS', 0, 1, NULL)",
        )
        assertThrows(IllegalArgumentException::class.java) { runBlocking { db.savedPostDao().pageAfter("", 100) } }

        val outcome = migrator().migrate(uid) as MigrationOutcome.Migrated

        assertEquals(5, outcome.posts)
        assertEquals(1, outcome.skipped)
        assertEquals((1..5).map { "post-$it" }.toSet(), serverIds(firestore.posts(uid)))
        assertEquals(setOf("POSTS:post-zz:UNREADABLE"), store.read().skipped)
        assertTrue(store.read().completed)
        assertEquals("Room 행은 그대로 남아 있다", 6, db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM saved_posts").use { it.moveToFirst(); it.getInt(0) })
    }

    // ── 완료 표시와 uid ──

    @Test
    fun 같은_uid로_두_번_실행하면_두번째는_아무것도_쓰지_않는다() = runBlocking {
        seed()
        var started = 0
        migrator().migrate(uid) { started++ }
        firestore.places(uid).document("place-1").update(placeUpdate("name" to "고친 이름")).await()

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

    /**
     * 규칙이 거부하고 **고칠 수 없는** 게시물(extractedCount가 상한 1000 초과). 캡션 앞에 SECRET을 붙여서 기록에 내용이 새지 않는지 확인한다.
     * (캡션이 긴 것, 이름이 긴 것은 이제 고쳐서 옮긴다 — 아래 sanitize 테스트 참고)
     */
    private fun unfixablePost(id: String) = SavedPostEntity(
        id = id, shortcode = "SC-$id", instagramUrl = "https://www.instagram.com/p/SC-$id/", caption = SECRET + "가".repeat(50),
        thumbnailUrl = null, status = ResolveStatus.UNRESOLVED, extractedCount = 5_000, createdAt = 2_000L,
        unresolvedReason = UnresolvedReason.PLACE_NOT_FOUND,
    )

    // ── 영구 종료 플래그(로그아웃·계정 삭제·계정 전환) ──

    @Test
    fun 영구_종료_플래그가_켜지면_새_익명_uid에서도_Room이_서버로_올라가지_않는다() = runBlocking<Unit> {
        seed()
        store.markClosed() // 로그아웃·삭제·전환 직전에 켜진다

        val outcome = migrator().migrate(uid, isAnonymous = true)

        assertEquals(MigrationOutcome.Closed, outcome)
        assertTrue(serverIds(firestore.folders(uid)).isEmpty())
        assertTrue(serverIds(firestore.places(uid)).isEmpty())
        assertTrue(serverIds(firestore.posts(uid)).isEmpty())
        assertFalse("이전 기록도 시작되지 않는다", store.read().completed)
        assertEquals("Room은 그대로", 5, db.placeDao().count())
    }

    @Test
    fun 영구_종료_플래그는_이전_도중이던_uid에도_적용된다() = runBlocking<Unit> {
        seed()
        store.begin(uid, 1L) // 이 uid로 이전을 시작한 기록이 있어도
        store.markClosed()

        assertEquals(MigrationOutcome.Closed, migrator().migrate(uid, isAnonymous = true))
        assertTrue(serverIds(firestore.places(uid)).isEmpty())
    }

    @Test
    fun 영구_종료_플래그는_begin과_markCompleted로_지워지지_않는다() = runBlocking<Unit> {
        store.markClosed()

        store.begin("other-uid", 1L)
        assertTrue(store.read().closed)
        store.markCompleted("another-uid")
        assertTrue(store.read().closed)
    }

    @Test
    fun 플래그가_없을_때_로그인한_계정이_처음_보는_uid면_이전하지_않는다() = runBlocking<Unit> {
        seed()

        val outcome = migrator().migrate(uid, isAnonymous = false)

        assertEquals(MigrationOutcome.Closed, outcome)
        assertTrue(serverIds(firestore.places(uid)).isEmpty())
    }

    @Test
    fun 플래그가_없을_때_이전을_시작한_uid가_계정을_연결해도_이어서_끝낸다() = runBlocking<Unit> {
        seed()
        store.begin(uid, 1L) // 익명일 때 시작한 이전(uid는 연결해도 그대로)

        val outcome = migrator().migrate(uid, isAnonymous = false)

        assertTrue(outcome is MigrationOutcome.Migrated)
        assertEquals(5, serverIds(firestore.places(uid)).size)
    }

    @Test
    fun 플래그가_없는_기존_흐름은_익명이면_uid가_바뀌어도_처음부터_다시_이전한다() = runBlocking<Unit> {
        seed()
        store.begin("old-uid-from-backup", 1L)

        val outcome = migrator().migrate(uid, isAnonymous = true)

        assertTrue(outcome is MigrationOutcome.Migrated)
        assertEquals(5, serverIds(firestore.places(uid)).size)
    }

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
