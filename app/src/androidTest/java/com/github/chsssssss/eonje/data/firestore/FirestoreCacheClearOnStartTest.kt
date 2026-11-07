package com.github.chsssssss.eonje.data.firestore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsssssss.eonje.data.account.FirestoreCacheClearFlag
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "다음 콜드 스타트에 Firestore 캐시를 지운다"가 실제로 되는지 확인한다.
 *
 * 앱 프로세스를 다시 시작하는 건 이 테스트가 흉내낸다: Firestore 인스턴스를 `terminate`로 닫고 `getInstance`로 새로 받는다
 * (= 아직 아무것도 안 한 새 클라이언트, 곧 콜드 스타트 직후의 상태). **FirebaseApp은 지우지 않는다** — Auth SDK가 전역 상태를 첫
 * FirebaseApp에 묶어서, 앱을 지우면 같은 프로세스의 이후 테스트가 깨진다(FirebaseEmulatorEnv의 주석 참고).
 * 영구 캐시는 앱 이름·앱 ID·프로젝트로 정해진 디스크 파일이라 새 인스턴스에서도 그대로 남는다(첫 테스트가 그걸 확인한다).
 * 테스트마다 앱 이름이 달라서 서로의 캐시가 섞이지 않는다. 에뮬레이터(Auth+Firestore)를 쓰고, 운영 서버에는 붙지 않는다.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreCacheClearOnStartTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val env = FirebaseEmulatorEnv
    private lateinit var app: FirebaseApp

    @Before
    fun setUp() {
        assumeTrue("Firebase Emulator Suite가 호스트에서 실행 중이어야 한다", env.reachable())
        FirestoreCacheClearFlag.clear(context)
        app = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder().setProjectId(env.PROJECT).setApplicationId("1:1234567890:android:abcdef0123456789").setApiKey("fake-api-key").build(),
            "cache-clear-test-${System.nanoTime()}",
        )
        FirebaseAuth.getInstance(app).useEmulator(env.HOST, env.AUTH_PORT)
    }

    @After
    fun tearDown() {
        FirestoreCacheClearFlag.clear(context) // 실제 앱의 다음 시작에 영향이 없게
        if (::app.isInitialized) runCatching { runBlocking { FirebaseFirestore.getInstance(app).terminate().await() } }
    }

    /** 앱 시작 때의 설정 단계와 같은 모양(영구 캐시 → 에뮬레이터). 운영의 FirestoreSetup.configure는 에뮬레이터 연결이 빌드 옵션에 달려 있어서 직접 쓰지 않는다. */
    private val persistentEmulatorConfigure: (FirebaseApp) -> Unit = { target ->
        val firestore = FirebaseFirestore.getInstance(target)
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
            .build()
        firestore.useEmulator(env.HOST, env.FIRESTORE_PORT)
    }

    /** 프로세스 종료: Firestore 인스턴스를 닫는다(디스크의 영구 캐시는 남는다). 다음 getInstance는 새 클라이언트를 준다. */
    private suspend fun shutDown() {
        FirebaseFirestore.getInstance(app).terminate().await()
    }

    /** 첫 실행: 캐시에 문서 하나가 남도록 쓰고(서버 확정까지) 프로세스를 끝낸다. 쓴 사용자의 uid를 돌려준다. */
    private suspend fun firstRunWritesOneDocument(): String {
        persistentEmulatorConfigure(app)
        val uid = FirebaseAuth.getInstance(app).signInAnonymously().await().user!!.uid
        val firestore = FirebaseFirestore.getInstance(app)
        val place = Place("cached-1", "캐시에 남을 가게", null, null, null, "k1", null, ResolveStatus.RESOLVED, 1L, 2L)
        firestore.places(uid).document(place.id).set(place.toFields()).await()
        firestore.waitForPendingWrites().await()
        assertEquals("쓴 직후엔 캐시에 있다", 1, firestore.places(uid).get(Source.CACHE).await().size())
        shutDown()
        return uid
    }

    private suspend fun cachedCount(uid: String): Int =
        FirebaseFirestore.getInstance(app).places(uid).get(Source.CACHE).await().size()

    private suspend fun awaitFlagCleared() = withTimeout(15_000) {
        while (FirestoreCacheClearFlag.isRequested(context)) delay(50)
    }

    @Test
    fun 플래그가_없으면_재시작해도_영구_캐시가_그대로_남는다() = runBlocking<Unit> {
        val uid = firstRunWritesOneDocument()

        var configured = 0
        FirestoreSetup.initialize(app, context) { persistentEmulatorConfigure(it); configured++ }

        assertEquals("설정 단계가 실행됐다", 1, configured)
        assertEquals("플래그가 없으면 캐시를 지우지 않는다", 1, cachedCount(uid))
    }

    @Test
    fun 플래그가_있으면_재시작_때_설정_뒤_첫_사용_전에_캐시를_지우고_플래그도_지운다() = runBlocking<Unit> {
        val uid = firstRunWritesOneDocument()
        FirestoreCacheClearFlag.request(context) // 로그아웃·삭제가 남긴 표시

        val order = mutableListOf<String>()
        FirestoreSetup.initialize(app, context) { persistentEmulatorConfigure(it); order += "configure" }
        order += "initialize-returned"
        awaitFlagCleared() // clearPersistence가 성공해야 플래그를 지운다

        assertEquals(listOf("configure", "initialize-returned"), order)
        assertFalse("플래그가 지워졌다", FirestoreCacheClearFlag.isRequested(context))
        assertEquals("캐시가 비었다", 0, cachedCount(uid))
    }

    @Test
    fun 이미_Firestore를_쓴_뒤에는_캐시를_못_지우고_플래그를_남겨_다음_시작에_다시_시도한다() = runBlocking<Unit> {
        val uid = firstRunWritesOneDocument()
        FirestoreCacheClearFlag.request(context)

        // 같은 프로세스에서 이미 Firestore를 쓴 뒤라면(= 잘못된 순서) clearPersistence는 거부된다.
        persistentEmulatorConfigure(app)
        assertEquals(1, cachedCount(uid)) // 첫 사용
        try {
            FirebaseFirestore.getInstance(app).clearPersistence().await()
            fail("이미 시작한 클라이언트의 clearPersistence는 실패해야 한다")
        } catch (e: FirebaseFirestoreException) {
            assertEquals(FirebaseFirestoreException.Code.FAILED_PRECONDITION, e.code)
        }
        FirestoreSetup.clearCacheIfRequested(app, context) // 앱이 같은 상황이면 이 호출도 실패한다
        delay(2_000)
        assertTrue("실패하면 플래그를 지우지 않는다", FirestoreCacheClearFlag.isRequested(context))
        assertEquals("캐시도 그대로", 1, cachedCount(uid))
        shutDown()

        // 다음 콜드 스타트에서는 제대로 지워진다.
        FirestoreSetup.initialize(app, context, persistentEmulatorConfigure)
        awaitFlagCleared()
        assertEquals(0, cachedCount(uid))
    }
}
