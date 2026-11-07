package com.github.chsssssss.eonje.data.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.account.merge.AccountMergeIo
import com.github.chsssssss.eonje.data.account.merge.AccountMergeJournal
import com.github.chsssssss.eonje.data.account.merge.AccountMergePlanner
import com.github.chsssssss.eonje.data.account.merge.JournalData
import com.github.chsssssss.eonje.data.account.merge.MergeStage
import com.github.chsssssss.eonje.data.account.merge.TargetSnapshot
import com.github.chsssssss.eonje.data.local.AndroidImageCodec
import com.github.chsssssss.eonje.data.firestore.AuthSession
import com.github.chsssssss.eonje.data.firestore.FirebaseEmulatorEnv
import com.github.chsssssss.eonje.data.firestore.folders
import com.github.chsssssss.eonje.data.firestore.places
import com.github.chsssssss.eonje.data.firestore.posts
import com.github.chsssssss.eonje.data.firestore.toFields
import com.github.chsssssss.eonje.data.migration.RoomMigrationStateStore
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.repository.MergeCounts
import com.github.chsssssss.eonje.domain.repository.MergePreview
import com.github.chsssssss.eonje.domain.repository.SwitchResult
import okhttp3.OkHttpClient
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.repository.DeleteResult
import com.github.chsssssss.eonje.domain.repository.DeleteStep
import com.github.chsssssss.eonje.domain.repository.LinkResult
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 계정 연결·로그아웃·삭제를 Firebase Emulator Suite(Auth+Firestore)로 확인한다. 실제 Google 로그인은 자동화하지 않는다 —
 * Auth 에뮬레이터는 JSON 문자열을 가짜 Google ID 토큰으로 받는다(`{"sub":..., "email":...}`).
 * 에뮬레이터가 안 떠 있으면 건너뛴다.
 */
@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {
    private val env = FirebaseEmulatorEnv
    private val firestore get() = env.firestore
    private val auth get() = env.auth

    private lateinit var scope: CoroutineScope
    private lateinit var dir: File
    private lateinit var store: RoomMigrationStateStore
    private lateinit var cleanup: FakeCleanup
    private lateinit var work: FakeWorkControl
    private lateinit var repo: AccountRepositoryImpl
    private lateinit var journal: AccountMergeJournal
    private lateinit var mergeIo: AccountMergeIo
    private lateinit var thumbDir: File
    private lateinit var oldUid: String

    @Before
    fun setUp() = runBlocking<Unit> {
        assumeTrue("Firebase Emulator Suite가 호스트에서 실행 중이어야 한다", env.reachable())
        firestore.enableNetwork().await()
        auth.signOut()
        oldUid = auth.signInAnonymously().await().user!!.uid

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = File(context.cacheDir, "account-test-${System.nanoTime()}").apply { mkdirs() }
        store = RoomMigrationStateStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(dir, "m.preferences_pb") }))
        cleanup = FakeCleanup()
        work = FakeWorkControl()
        journal = AccountMergeJournal(File(dir, "journal.json"))
        thumbDir = File(dir, "thumbs").apply { mkdirs() }
        mergeIo = AccountMergeIo(firestore, ThumbnailStore(thumbDir, OkHttpClient(), AndroidImageCodec))
        repo = AccountRepositoryImpl(auth, firestore, store, cleanup, work, AuthSession(auth, scope), journal, mergeIo, scope)
    }

    @After
    fun tearDown() {
        if (::scope.isInitialized) scope.cancel()
        if (::dir.isInitialized) dir.deleteRecursively()
        if (env.isStarted) runCatching { runBlocking { firestore.enableNetwork().await() } }
    }

    // ── 연결 ──

    @Test
    fun 익명_계정에_연결해도_uid가_그대로고_데이터가_그대로다() = runBlocking {
        seedData(oldUid, places = 3)

        val result = repo.linkWithGoogle(fakeToken("link-keeps-uid"))

        assertEquals(LinkResult.Success, result)
        assertEquals("연결은 uid를 바꾸지 않는다", oldUid, auth.currentUser!!.uid)
        assertTrue(repo.state.value.isLinked)
        assertTrue(repo.state.value.email!!.startsWith("link-keeps-uid-"))
        assertEquals("데이터가 그대로", 3, serverCount(oldUid, "places"))
        assertFalse("연결은 Room 이전을 종료하지 않는다(uid가 같아서 이어갈 수 있다)", store.read().closed)
    }

    @Test
    fun 이미_다른_uid에_연결된_계정이면_충돌로_알린다() = runBlocking {
        assertEquals(LinkResult.Success, repo.linkWithGoogle(fakeToken("collision")))
        auth.signOut()
        auth.signInAnonymously().await()

        val result = repo.linkWithGoogle(fakeToken("collision"))

        assertEquals(LinkResult.Collision, result)
        assertTrue("충돌해도 익명 상태 그대로", auth.currentUser!!.isAnonymous)
    }

    // ── 로그아웃 ──

    @Test
    fun 로그아웃하면_Room_이전이_영구_종료되고_새_익명_uid로_시작하며_Room은_안_지운다() = runBlocking {
        assertEquals(LinkResult.Success, repo.linkWithGoogle(fakeToken("signout")))
        seedData(oldUid, places = 2)

        repo.signOut()

        assertTrue("영구 종료 플래그", store.read().closed)
        assertTrue(cleanup.deviceCachesCleared)
        assertTrue(cleanup.cacheClearRequested)
        assertFalse("로그아웃은 Room을 지우지 않는다", cleanup.roomCleared)
        val newUid = AuthSession(auth, scope).awaitUid(10_000)
        assertNotNull(newUid)
        assertNotEquals("새 익명 uid", oldUid, newUid)
        assertTrue(auth.currentUser!!.isAnonymous)
        assertEquals("서버 데이터는 그대로", 2, serverCount(oldUid, "places"))
    }

    @Test
    fun 익명이면_로그아웃은_아무것도_하지_않는다() = runBlocking {
        repo.signOut()

        assertEquals(oldUid, auth.currentUser!!.uid)
        assertFalse(store.read().closed)
        assertFalse(cleanup.deviceCachesCleared)
    }

    // ── 삭제 ──

    @Test
    fun 연결된_계정을_삭제하면_서버_데이터와_계정이_지워지고_Room도_비우고_영구_종료된다() = runBlocking {
        assertEquals(LinkResult.Success, repo.linkWithGoogle(fakeToken("delete-linked")))
        seedData(oldUid, places = 5, posts = 3, folders = 2)

        val result = repo.deleteAccount(fakeToken("delete-linked"))

        assertEquals(DeleteResult.Success, result)
        listOf("places", "posts", "folders").forEach { assertEquals("$it 가 지워졌다", 0, serverCount(oldUid, it)) }
        assertTrue("사용자가 요청한 삭제라서 Room도 비운다", cleanup.roomCleared)
        assertTrue(cleanup.deviceCachesCleared)
        assertTrue(cleanup.cacheClearRequested)
        assertTrue("영구 종료 플래그", store.read().closed)
        assertTrue("작업 일시정지 → 재개", work.paused && work.resumed)
        // 계정이 지워졌다: 같은 Google 토큰으로 다시 로그인하면 기존 계정이 아니라 새 사용자가 만들어진다
        auth.signOut()
        val again = auth.signInWithCredential(GoogleAuthProvider.getCredential(fakeToken("delete-linked"), null)).await()
        assertNotEquals(oldUid, again.user!!.uid)
    }

    @Test
    fun 익명_사용자의_데이터_삭제는_재인증_없이_지우고_새_익명_세션으로_시작한다() = runBlocking {
        seedData(oldUid, places = 4, posts = 2, folders = 1)

        val result = repo.deleteAccount(null)

        assertEquals(DeleteResult.Success, result)
        listOf("places", "posts", "folders").forEach { assertEquals(0, serverCount(oldUid, it)) }
        assertTrue(cleanup.roomCleared)
        assertTrue(store.read().closed)
        val newUid = AuthSession(auth, scope).awaitUid(10_000)
        assertNotNull(newUid)
        assertNotEquals(oldUid, newUid)
    }

    @Test
    fun 연결된_계정은_재인증_토큰이_없으면_아무것도_지우지_않고_REAUTH로_멈춘다() = runBlocking {
        assertEquals(LinkResult.Success, repo.linkWithGoogle(fakeToken("no-token")))
        seedData(oldUid, places = 2)

        val result = repo.deleteAccount(null)

        assertEquals(DeleteResult.Failed(DeleteStep.REAUTH, requiresReauth = true), result)
        assertEquals("데이터는 그대로", 2, serverCount(oldUid, "places"))
        assertFalse(store.read().closed)
        assertFalse(cleanup.roomCleared)
        assertTrue("실패해도 작업은 재개된다", work.resumed)
    }

    @Test
    fun 다른_계정의_토큰으로_재인증하면_REAUTH로_멈추고_데이터는_그대로다() = runBlocking {
        assertEquals(LinkResult.Success, repo.linkWithGoogle(fakeToken("real-owner")))
        seedData(oldUid, places = 2)

        val result = repo.deleteAccount(fakeToken("someone-else"))

        assertEquals(DeleteResult.Failed(DeleteStep.REAUTH, requiresReauth = true), result)
        assertEquals(2, serverCount(oldUid, "places"))
    }

    // ── 충돌: 이미 다른 uid에 연결된 계정으로 전환 + 합치기 ──

    /** 계정(합쳐질 쪽)과 이 기기(익명 uid)의 데이터를 만들고, 충돌 직전(익명 상태, 같은 토큰으로 link 시도)까지 간다. 계정 uid를 돌려준다. */
    private suspend fun setUpCollision(token: String): String {
        assertEquals(LinkResult.Success, repo.linkWithGoogle(token))
        val acctUid = oldUid
        // 계정 쪽: 폴더 "맛집", 장소 K1(게시물 s-acct 연결), 게시물 s-acct(shortcode SAME)
        put(acctUid, "folders", "f-acct", Folder("f-acct", "맛집", 1L).toFields())
        put(acctUid, "places", "p-acct", Place("p-acct", "계정 장소", "서울", 37.5, 127.0, "K1", null, ResolveStatus.RESOLVED, 1L, 2L, "f-acct", listOf("s-acct")).toFields())
        put(acctUid, "posts", "s-acct", SavedPost("s-acct", "SAME", "https://www.instagram.com/p/SAME/", "계정 캡션", null, ResolveStatus.RESOLVED, 0, 1L, null).toFields(listOf("p-acct"), emptyList()))
        // 이 기기(새 익명 사용자)
        auth.signOut()
        val anonUid = auth.signInAnonymously().await().user!!.uid
        put(anonUid, "folders", "f-anon", Folder("f-anon", "맛집", 1L).toFields()) // 이름이 같다 → 계정 쪽을 쓴다
        put(anonUid, "folders", "f-extra", Folder("f-extra", "회사 근처", 2L).toFields())
        put(anonUid, "places", "p-anon-1", Place("p-anon-1", "익명 장소1", "서울", 37.5, 127.0, "K1", null, ResolveStatus.RESOLVED, 1L, 2L, "f-anon", listOf("s-anon-same")).toFields())
        put(anonUid, "places", "p-anon-2", Place("p-anon-2", "익명 장소2", "서울", 37.6, 127.1, "K2", null, ResolveStatus.RESOLVED, 1L, 2L, "f-extra", listOf("s-anon-new")).toFields())
        put(anonUid, "posts", "s-anon-same", SavedPost("s-anon-same", "SAME", "https://www.instagram.com/p/SAME/", "익명 캡션", null, ResolveStatus.RESOLVED, 0, 1L, null).toFields(listOf("p-anon-1"), emptyList()))
        val candidates = (0..1).map { ExtractedCandidate("s-anon-new", 0, "후보", it, PlaceCandidate("kc-$it", "후보 $it", "주소", null, 37.5, 127.0)) }
        put(anonUid, "posts", "s-anon-new", SavedPost("s-anon-new", "NEW", "https://www.instagram.com/p/NEW/", "새 캡션", null, ResolveStatus.NEEDS_REVIEW, 2, 1L, null).toFields(listOf("p-anon-2"), candidates))
        File(thumbDir, "s-anon-same.jpg").writeBytes(ByteArray(10) { 1 }) // 버려지는 게시물의 썸네일 — 계정 쪽 게시물(s-acct)에 없으니 옮겨진다
        assertEquals(LinkResult.Collision, repo.linkWithGoogle(token))
        return acctUid
    }

    @Test
    fun 충돌_뒤_합치면_계정_쪽을_우선하고_나머지를_계정에_추가한다() = runBlocking {
        val acctUid = setUpCollision(fakeToken("merge"))

        val preview = repo.prepareCollisionMerge()!!
        assertEquals(MergePreview(folders = 2, places = 2, posts = 2), preview)
        assertTrue("합치기 전에 저널이 있다", journal.exists())

        val result = repo.switchToExistingAccount(merge = true)

        assertEquals(SwitchResult.Switched(MergeCounts(folders = 1, places = 1, posts = 1)), result)
        assertEquals("계정으로 전환했다", acctUid, auth.currentUser!!.uid)
        assertEquals("폴더: 계정 것 + 새 이름 하나", 2, serverCount(acctUid, "folders"))
        assertEquals("장소: K1은 계정 것 하나, K2가 추가", 2, serverCount(acctUid, "places"))
        assertEquals("게시물: SAME은 계정 쪽 하나, NEW가 추가", 2, serverCount(acctUid, "posts"))
        // 계정 쪽 문서는 그대로
        assertEquals("계정 캡션", serverDoc(acctUid, "posts/s-acct").stringField("caption"))
        assertEquals(listOf("s-acct"), serverDoc(acctUid, "places/p-acct").stringArray("postIds"))
        // 새로 합쳐진 게시물: 후보까지 통째로, placeIds는 매핑된 장소 ID
        val merged = serverDoc(acctUid, "posts/s-anon-new")
        assertEquals(2, merged.fields().getJSONObject("candidates").getJSONObject("arrayValue").getJSONArray("values").length())
        assertEquals(listOf("p-anon-2"), merged.stringArray("placeIds"))
        // 새 장소: 폴더는 새 폴더 그대로, postIds는 게시물과 일관
        val place = serverDoc(acctUid, "places/p-anon-2")
        assertEquals("f-extra", place.stringField("folderId"))
        assertEquals(listOf("s-anon-new"), place.stringArray("postIds"))
        assertTrue("updatedAt이 서버 시각으로 찍혔다", place.hasField("updatedAt"))
        // 같은 이름 폴더는 계정 쪽(f-acct)을 쓰므로 f-anon은 만들어지지 않는다
        assertFalse(serverExists(acctUid, "folders/f-anon"))
        // 정리: 저널 삭제, 영구 종료 플래그, 버려진 게시물의 썸네일을 계정 쪽 게시물 이름으로 옮김, 썸네일 정리 재예약
        assertFalse(journal.exists())
        assertTrue(store.read().closed)
        assertTrue(File(thumbDir, "s-acct.jpg").isFile)
        assertTrue(cleanup.maintenanceScheduled)
    }

    @Test
    fun 충돌_뒤_합치지_않으면_계정_데이터는_그대로고_저널은_지워진다() = runBlocking {
        val acctUid = setUpCollision(fakeToken("no-merge"))
        repo.prepareCollisionMerge()

        val result = repo.switchToExistingAccount(merge = false)

        assertEquals(SwitchResult.Switched(merged = null), result)
        assertEquals(acctUid, auth.currentUser!!.uid)
        assertEquals(1, serverCount(acctUid, "folders"))
        assertEquals(1, serverCount(acctUid, "places"))
        assertEquals(1, serverCount(acctUid, "posts"))
        assertFalse(journal.exists())
        assertTrue("전환이므로 Room 이전은 영구 종료", store.read().closed)
    }

    @Test
    fun 이_기기에_데이터가_없으면_묻지_않고_저널도_만들지_않는다() = runBlocking {
        val token = fakeToken("empty-device")
        assertEquals(LinkResult.Success, repo.linkWithGoogle(token))
        auth.signOut()
        auth.signInAnonymously().await()
        assertEquals(LinkResult.Collision, repo.linkWithGoogle(token))

        val preview = repo.prepareCollisionMerge()!!

        assertTrue(preview.isEmpty)
        assertFalse(journal.exists())
    }

    @Test
    fun 사용자가_정하기_전에_앱이_다시_시작되면_저널을_버리고_익명_상태를_유지한다() = runBlocking {
        setUpCollision(fakeToken("undecided"))
        repo.prepareCollisionMerge()
        val anonUid = auth.currentUser!!.uid

        assertNull(repo.resumePendingMerge())

        assertFalse(journal.exists())
        assertEquals(anonUid, auth.currentUser!!.uid)
    }

    @Test
    fun 취소하면_저널이_지워진다() = runBlocking {
        setUpCollision(fakeToken("cancel"))
        repo.prepareCollisionMerge()

        repo.cancelCollisionMerge()

        assertFalse(journal.exists())
    }

    @Test
    fun 전환은_끝났는데_합치기_전에_앱이_죽었어도_다음_시작에_이어서_합친다() = runBlocking {
        val token = fakeToken("resume-switching")
        val acctUid = setUpCollision(token)
        repo.prepareCollisionMerge()
        // "합치기"를 고르고 전환까지 끝낸 직후 앱이 죽은 상태를 만든다: 저널은 SWITCHING, 로그인은 이미 계정.
        journal.write(journal.read()!!.copy(stage = MergeStage.SWITCHING))
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        assertEquals(acctUid, auth.currentUser!!.uid)

        val counts = repo.resumePendingMerge()

        assertEquals(MergeCounts(1, 1, 1), counts)
        assertEquals(2, serverCount(acctUid, "places"))
        assertEquals(2, serverCount(acctUid, "posts"))
        assertFalse(journal.exists())
    }

    @Test
    fun 전환_전에_죽었으면_uid가_그대로라_저널을_버린다() = runBlocking {
        setUpCollision(fakeToken("resume-not-switched"))
        repo.prepareCollisionMerge()
        journal.write(journal.read()!!.copy(stage = MergeStage.SWITCHING)) // 전환은 안 됨(아직 익명)

        assertNull(repo.resumePendingMerge())

        assertFalse(journal.exists())
    }

    @Test
    fun 합치기_도중에_끊겨서_같은_계획을_다시_써도_중복과_손실이_없다() = runBlocking {
        val token = fakeToken("idempotent")
        val acctUid = setUpCollision(token)
        repo.prepareCollisionMerge()
        val snapshot = journal.read()!!.snapshot
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        val plan = AccountMergePlanner.plan(snapshot, mergeIo.readTarget(acctUid, snapshot))
        // 일부만 쓰고 끊긴 상태(폴더·장소까지)를 만든 뒤, MERGING 저널로 재개한다
        mergeIo.execute(acctUid, plan.copy(postWrites = emptyList()))
        journal.write(JournalData(oldUid, MergeStage.MERGING, snapshot, targetUid = acctUid, plan = plan))

        val counts = repo.resumePendingMerge()

        assertEquals(MergeCounts(1, 1, 1), counts)
        // 한 번 더 같은 계획을 써도 그대로다(멱등)
        mergeIo.execute(acctUid, plan)
        assertEquals(2, serverCount(acctUid, "folders"))
        assertEquals(2, serverCount(acctUid, "places"))
        assertEquals(2, serverCount(acctUid, "posts"))
        assertEquals(listOf("s-anon-new"), serverDoc(acctUid, "places/p-anon-2").stringArray("postIds"))
        assertEquals(listOf("s-acct"), serverDoc(acctUid, "places/p-acct").stringArray("postIds"))
    }

    @Test
    fun 다른_계정으로_바뀐_뒤의_MERGING_저널은_쓰지_않고_버린다() = runBlocking {
        val token = fakeToken("wrong-target")
        setUpCollision(token)
        repo.prepareCollisionMerge()
        val snapshot = journal.read()!!.snapshot
        journal.write(JournalData(oldUid, MergeStage.MERGING, snapshot, targetUid = "some-other-uid", plan = AccountMergePlanner.plan(snapshot, TargetSnapshot())))
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
        val acctUid = auth.currentUser!!.uid

        assertNull(repo.resumePendingMerge())

        assertFalse(journal.exists())
        assertEquals("엉뚱한 계정에 쓰지 않았다", 1, serverCount(acctUid, "places"))
    }

    // ── 도우미 ──

    private suspend fun put(uid: String, collection: String, id: String, fields: Map<String, Any?>) {
        firestore.collection("users").document(uid).collection(collection).document(id).set(fields).await()
    }

    private fun serverDoc(uid: String, path: String): JSONObject {
        val url = URL("http://${FirebaseEmulatorEnv.HOST}:${FirebaseEmulatorEnv.FIRESTORE_PORT}/v1/projects/${FirebaseEmulatorEnv.PROJECT}/databases/(default)/documents/users/$uid/$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer owner")
        return try {
            JSONObject(connection.inputStream.bufferedReader().readText())
        } finally {
            connection.disconnect()
        }
    }

    private fun serverExists(uid: String, path: String): Boolean {
        val url = URL("http://${FirebaseEmulatorEnv.HOST}:${FirebaseEmulatorEnv.FIRESTORE_PORT}/v1/projects/${FirebaseEmulatorEnv.PROJECT}/databases/(default)/documents/users/$uid/$path")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer owner")
        return try { connection.responseCode == 200 } finally { connection.disconnect() }
    }

    private fun JSONObject.fields(): JSONObject = getJSONObject("fields")
    private fun JSONObject.hasField(name: String) = fields().has(name)
    private fun JSONObject.stringField(name: String): String = fields().getJSONObject(name).getString("stringValue")
    private fun JSONObject.stringArray(name: String): List<String> {
        val values = fields().getJSONObject(name).getJSONObject("arrayValue").getJSONArray("values")
        return (0 until values.length()).map { values.getJSONObject(it).getString("stringValue") }
    }

    // 같은 이름(sub)은 한 테스트 안에서 같은 토큰이다. 이메일에도 고유값을 넣는다 — 에뮬레이터를 다시 띄우지 않고 테스트를 또 돌려도
    // "같은 이메일의 다른 계정"과 충돌하지 않게(충돌 테스트는 같은 토큰을 두 번 쓰는 걸로 만든다).
    private fun fakeToken(sub: String): String = tokens.getOrPut(sub) {
        val unique = "$sub-${System.nanoTime()}"
        """{"sub":"$unique","email":"$unique@example.com","email_verified":true}"""
    }

    private val tokens = HashMap<String, String>()

    private suspend fun seedData(uid: String, places: Int = 0, posts: Int = 0, folders: Int = 0) {
        val batch = firestore.batch()
        (1..places).forEach { n ->
            batch.set(firestore.places(uid).document("p$n"), Place("p$n", "가게 $n", null, null, null, "k$n", null, ResolveStatus.RESOLVED, 1L, 2L).toFields())
        }
        (1..posts).forEach { n ->
            batch.set(firestore.posts(uid).document("s$n"), SavedPost("s$n", "SC$n", "https://www.instagram.com/p/SC$n/", "캡션", null, ResolveStatus.RESOLVED, 0, 1L, null).toFields())
        }
        (1..folders).forEach { n -> batch.set(firestore.folders(uid).document("f$n"), Folder("f$n", "폴더 $n", 1L).toFields()) }
        batch.commit().await()
    }

    /** 에뮬레이터 REST(규칙 우회)로 서버에 있는 문서 수를 센다. */
    private fun serverCount(uid: String, collection: String): Int {
        val url = URL("http://${FirebaseEmulatorEnv.HOST}:${FirebaseEmulatorEnv.FIRESTORE_PORT}/v1/projects/${FirebaseEmulatorEnv.PROJECT}/databases/(default)/documents/users/$uid/$collection?pageSize=300")
        val connection = url.openConnection() as HttpURLConnection
        connection.setRequestProperty("Authorization", "Bearer owner")
        return try {
            JSONObject(connection.inputStream.bufferedReader().readText()).optJSONArray("documents")?.length() ?: 0
        } finally {
            connection.disconnect()
        }
    }

    private class FakeCleanup : AccountLocalCleanup {
        var deviceCachesCleared = false
        var roomCleared = false
        var cacheClearRequested = false
        var maintenanceScheduled = false
        override suspend fun clearDeviceCaches() { deviceCachesCleared = true }
        override suspend fun clearLegacyRoom() { roomCleared = true }
        override fun requestFirestoreCacheClear() { cacheClearRequested = true }
        override fun scheduleThumbnailMaintenance() { maintenanceScheduled = true }
    }

    private class FakeWorkControl : AccountWorkControl {
        var paused = false
        var resumed = false
        override fun pause() { paused = true }
        override fun resume() { resumed = true }
    }
}
