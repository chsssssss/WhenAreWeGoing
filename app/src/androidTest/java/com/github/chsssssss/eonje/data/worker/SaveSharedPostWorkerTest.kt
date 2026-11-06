package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PostWorkScheduler
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.UserSession
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostResult
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostUseCase
import com.github.chsssssss.eonje.domain.util.ShareKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** SaveSharedPostWorker를 가짜 로그인·저장소로 돌려서, 로그인 전 공유가 유실되지 않고 한 번만 저장되는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class SaveSharedPostWorkerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val url = "https://www.instagram.com/p/ABC123/"

    private class Session(var signedIn: Boolean, private val failuresBeforeSuccess: Int) : UserSession {
        var attempts = 0
        override fun isSignedIn() = signedIn
        override suspend fun signInIfNeeded(): Boolean {
            if (signedIn) return true
            attempts++
            if (attempts > failuresBeforeSuccess) signedIn = true
            return signedIn
        }
    }

    private class Repo(private val session: Session) : SavedPostRepository {
        val posts = linkedMapOf<String, SavedPost>()
        override suspend fun findByShortcode(shortcode: String) = posts.values.firstOrNull { it.shortcode == shortcode }
        override suspend fun findById(id: String) = posts[id]
        override suspend fun save(post: SavedPost) {
            if (!session.signedIn) throw NotSignedInException()
            posts[post.id] = post
        }
        override suspend fun findByIds(ids: List<String>): List<SavedPost> = TODO()
        override suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?) = TODO()
        override suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int) = TODO()
        override suspend fun deleteByIds(ids: List<String>) = TODO()
        override suspend fun clearLocalThumbnail(id: String) = TODO()
        override fun observeAll(): Flow<List<SavedPost>> = emptyFlow()
        override suspend fun cachedPostIds(): Set<String> = TODO()
        override suspend fun loadCachedUnresolved(): List<SavedPost> = TODO()
        override fun observeUnresolved(): Flow<List<SavedPost>> = emptyFlow()
    }

    private class Scheduler : PostWorkScheduler {
        val captionParsing = mutableListOf<String>()
        override fun scheduleCaptionParsing(postId: String) { captionParsing += postId }
        override fun scheduleSaveSharedPost(shareKey: String, url: String, shortcode: String?, createdAt: Long) = Unit
    }

    private fun worker(session: Session, repo: Repo, scheduler: Scheduler): SaveSharedPostWorker {
        val useCase = SaveSharedPostUseCase(repo, scheduler, session)
        return TestListenableWorkerBuilder<SaveSharedPostWorker>(context)
            .setInputData(SaveSharedPostWorker.inputData(url, "ABC123", 1234L))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker =
                    SaveSharedPostWorker(appContext, workerParameters, session, useCase)
            })
            .build()
    }

    @Test
    fun 로그인이_안_돼_있으면_Worker가_직접_로그인을_시도하고_실패하면_재시도하며_저장하지_않는다() = runBlocking {
        val session = Session(signedIn = false, failuresBeforeSuccess = Int.MAX_VALUE)
        val repo = Repo(session)

        val result = worker(session, repo, Scheduler()).doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals("Worker가 로그인을 시도해야 한다", 1, session.attempts)
        assertTrue(repo.posts.isEmpty())
    }

    @Test
    fun 로그인에_성공하면_공유한_URL이_저장되고_캡션_조회가_예약된다() = runBlocking {
        val session = Session(signedIn = false, failuresBeforeSuccess = 1)
        val repo = Repo(session)
        val scheduler = Scheduler()

        assertEquals(ListenableWorker.Result.retry(), worker(session, repo, scheduler).doWork()) // 1차: 오프라인
        assertTrue(repo.posts.isEmpty())
        assertEquals(ListenableWorker.Result.success(), worker(session, repo, scheduler).doWork()) // 2차: 연결됨

        val post = repo.posts.values.single()
        assertEquals(2, session.attempts)
        assertEquals(url, post.instagramUrl)
        assertEquals(ResolveStatus.PENDING, post.status)
        assertEquals(1234L, post.createdAt)
        assertEquals(listOf(post.id), scheduler.captionParsing)
    }

    @Test
    fun 같은_URL을_두_번_넣어도_게시물은_한_번만_만들어진다() = runBlocking {
        val session = Session(signedIn = true, failuresBeforeSuccess = 0)
        val repo = Repo(session)
        val scheduler = Scheduler()

        assertEquals(ListenableWorker.Result.success(), worker(session, repo, scheduler).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(session, repo, scheduler).doWork())

        assertEquals(1, repo.posts.size)
        assertEquals(ShareKey.postId("ABC123"), repo.posts.keys.single())
        assertEquals("재실행해도 같은 게시물에 대한 예약 대상은 하나다", 1, scheduler.captionParsing.toSet().size)
    }

    @Test
    fun Worker가_저장한_뒤_같은_링크를_직접_공유해도_중복되지_않는다() = runBlocking {
        val session = Session(signedIn = true, failuresBeforeSuccess = 0)
        val repo = Repo(session)
        val scheduler = Scheduler()
        worker(session, repo, scheduler).doWork()

        val direct = SaveSharedPostUseCase(repo, scheduler, session)("$url?igsh=zzz")

        assertEquals(SaveSharedPostResult.AlreadySaved, direct)
        assertEquals(1, repo.posts.size)
    }
}
