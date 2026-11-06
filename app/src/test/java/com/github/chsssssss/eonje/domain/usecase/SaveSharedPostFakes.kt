package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PostWorkScheduler
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** [signInFailuresBeforeSuccess]번 실패한 뒤 성공하는 로그인. 시도 횟수를 센다. */
class FakeUserSession(
    var signedIn: Boolean = false,
    private val signInFailuresBeforeSuccess: Int = 0,
) : UserSession {
    var signInAttempts = 0
        private set

    override fun isSignedIn() = signedIn
    override fun currentUid(): String? = if (signedIn) "uid" else null
    override val uid: kotlinx.coroutines.flow.StateFlow<String?>
        get() = kotlinx.coroutines.flow.MutableStateFlow(currentUid())

    override suspend fun signInIfNeeded(): Boolean {
        if (signedIn) return true
        signInAttempts++
        if (signInAttempts > signInFailuresBeforeSuccess) signedIn = true
        return signedIn
    }
}

class FakeSavedPostRepository(private val session: FakeUserSession) : SavedPostRepository {
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
    override suspend fun findIdsAbsentOnServer(ids: Collection<String>): Set<String> = TODO()
    override suspend fun loadCachedUnresolved(): List<SavedPost> = TODO()
    override fun observeUnresolved(): Flow<List<SavedPost>> = emptyFlow()
}

class FakePostWorkScheduler : PostWorkScheduler {
    val captionParsing = mutableListOf<String>()
    data class Queued(val key: String, val url: String, val shortcode: String?, val createdAt: Long)
    val queued = mutableListOf<Queued>()

    override fun scheduleCaptionParsing(postId: String) {
        captionParsing += postId
    }

    override fun scheduleSaveSharedPost(shareKey: String, url: String, shortcode: String?, createdAt: Long) {
        queued += Queued(shareKey, url, shortcode, createdAt)
    }
}

class FakeLegacyMigrationStatus(var complete: Boolean = true, var legacyShortcodes: Set<String> = emptySet()) :
    com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus {
    override suspend fun isComplete() = complete
    override suspend fun isLegacyDuplicate(shortcode: String) = !complete && shortcode in legacyShortcodes
}
