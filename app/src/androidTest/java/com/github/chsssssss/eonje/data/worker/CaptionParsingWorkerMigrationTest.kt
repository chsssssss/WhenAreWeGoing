package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.github.chsssssss.eonje.data.local.AndroidImageCodec
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.domain.notification.PlaceSavedNotifier
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.InstagramMetaLookupRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.usecase.MatchPostUseCase
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.lang.reflect.Proxy

/**
 * 업데이트 전에 예약된 CaptionParsingWorker가 Room → Firestore 이전보다 먼저 돌아서 게시물을 못 찾는 경우:
 * 이전이 안 끝났으면 "삭제됐다"가 아니라 "아직 안 옮겨졌다"일 수 있으므로 시도 횟수를 쓰지 않고 계속 기다려야 한다.
 */
@RunWith(AndroidJUnit4::class)
class CaptionParsingWorkerMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var dir: File
    private val repositoryCalls = mutableListOf<String>()

    private class Legacy(var complete: Boolean) : LegacyMigrationStatus {
        override suspend fun isComplete() = complete
        override suspend fun isLegacyDuplicate(shortcode: String) = false
    }

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "caption-worker-test-${System.nanoTime()}").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /** 부르면 실패하는 인터페이스 대역. 이 테스트에서 게시물을 못 찾으면 다른 의존성은 쓰이지 않는다. */
    private inline fun <reified T : Any> unused(): T = Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
        throw AssertionError("${T::class.java.simpleName}.${method.name}은 불리면 안 된다")
    } as T

    /** findById는 항상 null(게시물 없음). 그 밖의 호출은 기록하고 실패시킨다 — 특히 updateStatus(UNRESOLVED 처리)가 불리면 안 된다. */
    private fun postsWithoutThePost(): SavedPostRepository = Proxy.newProxyInstance(
        SavedPostRepository::class.java.classLoader, arrayOf(SavedPostRepository::class.java),
    ) { _, method, _ ->
        if (method.name == "findById") null else {
            repositoryCalls += method.name
            throw AssertionError("SavedPostRepository.${method.name}은 불리면 안 된다")
        }
    } as SavedPostRepository

    private fun worker(legacy: Legacy, runAttempt: Int): CaptionParsingWorker {
        val useCase = MatchPostUseCase(
            postsWithoutThePost(), unused<InstagramMetaLookupRepository>(), unused<CaptionParsingRepository>(),
            unused<KakaoLocalRepository>(), unused<PlaceRepository>(), unused<ExtractedCandidateRepository>(),
            ThumbnailStore(dir, OkHttpClient(), AndroidImageCodec), unused<PlaceSavedNotifier>(),
            FirebaseAnalytics.getInstance(context), legacy,
        )
        return TestListenableWorkerBuilder<CaptionParsingWorker>(context)
            .setInputData(workDataOf("postId" to "pre-update-post"))
            .setRunAttemptCount(runAttempt)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    CaptionParsingWorker(appContext, workerParameters, useCase, com.github.chsssssss.eonje.data.account.AccountOperationGate())
            })
            .build()
    }

    @Test
    fun 이전이_안_끝났으면_게시물을_못_찾아도_재시도하고_횟수_한도에서도_포기하지_않는다() = runBlocking {
        val legacy = Legacy(complete = false)

        assertEquals(ListenableWorker.Result.retry(), worker(legacy, runAttempt = 0).doWork())
        // 일반 재시도의 한도(5회)를 넘은 시도에서도 UNRESOLVED로 종결하지 않는다.
        assertEquals(ListenableWorker.Result.retry(), worker(legacy, runAttempt = 9).doWork())
        assertTrue("게시물을 UNRESOLVED로 바꾸려는 쓰기가 없어야 한다", repositoryCalls.isEmpty())
    }

    @Test
    fun 이전이_끝났는데도_게시물이_없으면_삭제된_것이므로_조용히_끝낸다() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(Legacy(complete = true), runAttempt = 0).doWork())
        assertTrue(repositoryCalls.isEmpty())
    }
}
