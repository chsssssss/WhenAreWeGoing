package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.util.ShareKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SaveSharedPostUseCaseTest {
    private val url = "https://www.instagram.com/p/ABC123/"
    private val shared = "맛집 봐봐 https://www.instagram.com/p/ABC123/?igsh=xyz"

    private lateinit var session: FakeUserSession
    private lateinit var repository: FakeSavedPostRepository
    private lateinit var scheduler: FakePostWorkScheduler
    private lateinit var useCase: SaveSharedPostUseCase

    private fun setUp(signedIn: Boolean) {
        session = FakeUserSession(signedIn = signedIn)
        repository = FakeSavedPostRepository(session)
        scheduler = FakePostWorkScheduler()
        useCase = SaveSharedPostUseCase(repository, scheduler, session)
    }

    @Before
    fun signedInByDefault() = setUp(signedIn = true)

    @Test
    fun 로그인돼_있으면_바로_저장하고_캡션_조회를_예약한다() = runBlocking {
        assertEquals(SaveSharedPostResult.Saved, useCase(shared))

        val post = repository.posts.values.single()
        assertEquals(ResolveStatus.PENDING, post.status)
        assertEquals(url, post.instagramUrl)
        assertEquals(ShareKey.postId("ABC123"), post.id)
        assertEquals(listOf(post.id), scheduler.captionParsing)
        assertTrue(scheduler.queued.isEmpty())
    }

    @Test
    fun 로그인_전이면_기다리지_않고_URL을_Worker에_맡긴다() = runBlocking {
        setUp(signedIn = false)

        assertEquals(SaveSharedPostResult.SavedLater, useCase(shared))

        assertTrue("저장소는 건드리지 않는다", repository.posts.isEmpty())
        assertEquals(0, session.signInAttempts) // 메인 스레드에서 로그인을 기다리거나 시도하지 않는다
        val queued = scheduler.queued.single()
        assertEquals("ABC123", queued.key)
        assertEquals(url, queued.url)
        assertEquals("ABC123", queued.shortcode)
        assertTrue(scheduler.captionParsing.isEmpty())
    }

    @Test
    fun 저장_도중_로그인이_없다는_예외가_나도_Worker로_넘겨_유실하지_않는다() = runBlocking {
        // isSignedIn()은 true인데 저장소는 로그인 전이라고 답하는 경합 상황
        val racing = FakeUserSession(signedIn = true)
        val repo = FakeSavedPostRepository(FakeUserSession(signedIn = false))
        scheduler = FakePostWorkScheduler()

        val result = SaveSharedPostUseCase(repo, scheduler, racing)(shared)

        assertEquals(SaveSharedPostResult.SavedLater, result)
        assertEquals(1, scheduler.queued.size)
    }

    @Test
    fun 같은_링크를_다시_공유하면_새로_만들지_않는다() = runBlocking {
        assertEquals(SaveSharedPostResult.Saved, useCase(shared))
        assertEquals(SaveSharedPostResult.AlreadySaved, useCase("$url?utm_source=ig_web_copy_link"))

        assertEquals(1, repository.posts.size)
        assertEquals(1, scheduler.captionParsing.size)
    }

    @Test
    fun 인스타_게시물이_아니면_저장하지_않는다() = runBlocking {
        assertEquals(SaveSharedPostResult.NoUrlFound, useCase("https://example.com/p/abc"))
        assertTrue(repository.posts.isEmpty())
        assertTrue(scheduler.queued.isEmpty())
    }

    @Test
    fun Worker_경로는_shortcode가_없으면_URL_해시_키로_중복을_확인한다() = runBlocking {
        val odd = "https://www.instagram.com/p/NoShortcode/"
        val key = ShareKey.of(null, odd)
        assertTrue(key.startsWith("u-"))

        assertEquals(SaveSharedPostResult.Saved, useCase.saveQueued(odd, null, 1L))
        assertEquals(SaveSharedPostResult.AlreadySaved, useCase.saveQueued(odd, null, 2L))

        val post = repository.posts.values.single()
        assertEquals(ShareKey.postId(key), post.id)
        assertNull(post.shortcode)
        assertEquals(1L, post.createdAt) // 공유한 시각이 유지된다
    }

    @Test
    fun Worker가_저장은_했지만_캡션_예약_전에_죽었다면_다시_실행할_때_이어서_예약한다() = runBlocking {
        useCase.saveQueued(url, "ABC123", 1L)
        scheduler.captionParsing.clear() // 예약 직전에 죽은 상황

        assertEquals(SaveSharedPostResult.AlreadySaved, useCase.saveQueued(url, "ABC123", 1L))

        assertEquals(listOf(ShareKey.postId("ABC123")), scheduler.captionParsing)
        assertEquals(1, repository.posts.size)
    }

    @Test
    fun 공유_키는_shortcode가_없으면_URL이_같을_때만_같다() {
        assertEquals("ABC", ShareKey.of("ABC", "x"))
        assertEquals(ShareKey.of(null, "https://a/1"), ShareKey.of(null, "https://a/1"))
        assertTrue(ShareKey.of(null, "https://a/1") != ShareKey.of(null, "https://a/2"))
        assertTrue(ShareKey.of(" ", "https://a/1").startsWith("u-"))
    }
}
