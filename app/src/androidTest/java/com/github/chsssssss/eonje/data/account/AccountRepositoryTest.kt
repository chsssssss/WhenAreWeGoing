package com.github.chsssssss.eonje.data.account

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.chsssssss.eonje.data.firestore.AuthSession
import com.github.chsssssss.eonje.data.firestore.FirebaseEmulatorEnv
import com.github.chsssssss.eonje.data.firestore.folders
import com.github.chsssssss.eonje.data.firestore.places
import com.github.chsssssss.eonje.data.firestore.posts
import com.github.chsssssss.eonje.data.firestore.toFields
import com.github.chsssssss.eonje.data.migration.RoomMigrationStateStore
import com.github.chsssssss.eonje.domain.model.Folder
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
        repo = AccountRepositoryImpl(auth, firestore, store, cleanup, work, AuthSession(auth, scope), scope)
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
        assertEquals("link-keeps-uid@example.com", repo.state.value.email)
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

    // ── 도우미 ──

    private fun fakeToken(sub: String) = """{"sub":"$sub-${System.nanoTime()}","email":"$sub@example.com","email_verified":true}"""
        .let { json -> tokens.getOrPut(sub) { json } }

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
        override suspend fun clearDeviceCaches() { deviceCachesCleared = true }
        override suspend fun clearLegacyRoom() { roomCleared = true }
        override fun requestFirestoreCacheClear() { cacheClearRequested = true }
    }

    private class FakeWorkControl : AccountWorkControl {
        var paused = false
        var resumed = false
        override fun pause() { paused = true }
        override fun resume() { resumed = true }
    }
}
