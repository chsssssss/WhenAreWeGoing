package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.NotSignedInException
import com.github.chsssssss.eonje.domain.repository.PostWorkScheduler
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.UserSession
import com.github.chsssssss.eonje.domain.util.InstagramUrlParser
import com.github.chsssssss.eonje.domain.util.ShareKey
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

sealed interface SaveSharedPostResult {
    data object Saved : SaveSharedPostResult
    data object AlreadySaved : SaveSharedPostResult
    data object NoUrlFound : SaveSharedPostResult

    /** 아직 로그인 전이라 바로 저장하지 못해, URL을 WorkManager에 맡겼다. 로그인되는 대로 저장된다(유실 없음). */
    data object SavedLater : SaveSharedPostResult
}

class SaveSharedPostUseCase @Inject constructor(
    private val repository: SavedPostRepository,
    private val scheduler: PostWorkScheduler,
    private val session: UserSession,
    private val legacyMigration: LegacyMigrationStatus,
) {
    /** 공유 수신(메인 스레드)에서 부른다. 로그인 전이면 **기다리지 않고** 곧바로 Worker 경로로 간다. */
    suspend operator fun invoke(sharedText: String): SaveSharedPostResult {
        // 인스타 게시물(/p/, /reel/, /tv/) 링크만 받는다. 다른 URL(프로필, 외부 사이트 등)은 자동 매칭을 시작할 수 없어
        // 인박스에 "정리 대기"로 영원히 남기만 하므로 저장하지 않고 토스트로 알린다.
        val postUrl = InstagramUrlParser.findPostUrl(sharedText)
            ?: return SaveSharedPostResult.NoUrlFound
        val createdAt = System.currentTimeMillis()

        if (!session.isSignedIn()) return saveLater(postUrl.canonicalUrl, postUrl.shortcode, createdAt)
        return try {
            save(postUrl.canonicalUrl, postUrl.shortcode, createdAt, fromWorker = false)
        } catch (e: NotSignedInException) {
            saveLater(postUrl.canonicalUrl, postUrl.shortcode, createdAt) // 그 사이 로그인이 풀린 경우에도 유실하지 않는다
        }
    }

    /** [com.github.chsssssss.eonje.data.worker.SaveSharedPostWorker]가 부른다. 로그인 전이면 [NotSignedInException]을 던진다. */
    suspend fun saveQueued(url: String, shortcode: String?, createdAt: Long): SaveSharedPostResult =
        save(url, shortcode, createdAt, fromWorker = true)

    private fun saveLater(url: String, shortcode: String?, createdAt: Long): SaveSharedPostResult {
        scheduler.scheduleSaveSharedPost(ShareKey.of(shortcode, url), url, shortcode, createdAt)
        return SaveSharedPostResult.SavedLater
    }

    private suspend fun save(url: String, shortcode: String?, createdAt: Long, fromWorker: Boolean): SaveSharedPostResult {
        // 같은 링크를 연달아 공유하면 중복 확인과 저장 사이에 끼어들 수 있어서 프로세스 안에서 직렬화한다.
        return saveMutex.withLock {
            val postId = ShareKey.postId(ShareKey.of(shortcode, url))
            val existing = if (shortcode != null) repository.findByShortcode(shortcode) else repository.findById(postId)
            // 이전이 안 끝났으면 이미 저장해둔(아직 Firestore로 못 옮겨진) 게시물일 수 있다 — Room도 shortcode로 확인한다.
            if (existing == null && shortcode != null && legacyMigration.isLegacyDuplicate(shortcode)) {
                return@withLock SaveSharedPostResult.AlreadySaved
            }
            if (existing != null) {
                // Worker가 저장은 했는데 F2 예약 직전에 죽었다면 PENDING으로 남아 있다 — 이어서 예약한다(KEEP이라 중복 안 됨).
                if (fromWorker && existing.id == postId && existing.status == ResolveStatus.PENDING) {
                    scheduler.scheduleCaptionParsing(postId)
                }
                return@withLock SaveSharedPostResult.AlreadySaved
            }

            repository.save(
                SavedPost(
                    id = postId,
                    shortcode = shortcode,
                    instagramUrl = url,
                    caption = null,
                    thumbnailUrl = null,
                    status = ResolveStatus.PENDING,
                    extractedCount = 0,
                    createdAt = createdAt,
                )
            )
            // F2로 위임: 실패해도 저장 자체는 이미 끝났다.
            scheduler.scheduleCaptionParsing(postId)
            SaveSharedPostResult.Saved
        }
    }

    private companion object {
        val saveMutex = Mutex()
    }
}
