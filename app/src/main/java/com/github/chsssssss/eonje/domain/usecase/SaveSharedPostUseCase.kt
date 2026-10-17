package com.github.chsssssss.eonje.domain.usecase

import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.data.worker.CaptionParsingWorker
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.InstagramUrlParser
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject

sealed interface SaveSharedPostResult {
    data object Saved : SaveSharedPostResult
    data object AlreadySaved : SaveSharedPostResult
    data object NoUrlFound : SaveSharedPostResult
}

class SaveSharedPostUseCase @Inject constructor(
    private val repository: SavedPostRepository,
    private val workManager: WorkManager,
) {
    suspend operator fun invoke(sharedText: String): SaveSharedPostResult {
        // 인스타 게시물(/p/, /reel/, /tv/) 링크만 받는다. 다른 URL(프로필, 외부 사이트 등)은 자동 매칭을 시작할 수 없어
        // 인박스에 "정리 대기"로 영원히 남기만 하므로 저장하지 않고 토스트로 알린다.
        val postUrl = InstagramUrlParser.findPostUrl(sharedText)
            ?: return SaveSharedPostResult.NoUrlFound

        // 같은 링크를 연달아 공유하면 중복 확인과 저장 사이에 끼어들 수 있어서 프로세스 안에서 직렬화한다.
        return saveMutex.withLock {
            if (repository.findByShortcode(postUrl.shortcode) != null) {
                return@withLock SaveSharedPostResult.AlreadySaved
            }

            val postId = UUID.randomUUID().toString()
            repository.save(
                SavedPostEntity(
                    id = postId,
                    shortcode = postUrl.shortcode,
                    instagramUrl = postUrl.canonicalUrl,
                    caption = null,
                    thumbnailUrl = null,
                    status = ResolveStatus.PENDING,
                    extractedCount = 0,
                    createdAt = System.currentTimeMillis()
                )
            )

            // F2로 위임: 실패해도 저장 자체는 이미 끝났다.
            workManager.enqueue(CaptionParsingWorker.request(postId))
            SaveSharedPostResult.Saved
        }
    }

    private companion object {
        val saveMutex = Mutex()
    }
}
