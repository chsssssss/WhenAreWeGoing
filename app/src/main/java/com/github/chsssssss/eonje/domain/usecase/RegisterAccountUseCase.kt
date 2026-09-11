package com.github.chsssssss.eonje.domain.usecase

import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.local.TokenStatusStore
import com.github.chsssssss.eonje.data.local.WatchedAccountEntity
import com.github.chsssssss.eonje.data.worker.CaptionParsingWorker
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
import com.github.chsssssss.eonje.domain.repository.InstagramTokenExpiredException
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
import javax.inject.Inject

sealed interface RegisterAccountResult {
    data object Registered : RegisterAccountResult
    data class Failed(val message: String) : RegisterAccountResult
}

/**
 * F3: username → 프로페셔널 계정 확인(Business Discovery) → 등록 → 해당 계정 UNRESOLVED 게시물 재매칭.
 * 재매칭은 캡션 파싱(LLM)·카카오 검색이 걸린 게시물마다 순서대로 도는 무거운 작업이라,
 * 여기서 직접 기다리지 않고 CaptionParsingWorker로 위임한다 — 계정 등록 자체는
 * Business Discovery 확인만 끝나면 바로 응답한다. 인박스에는 "정리 중" 칩으로 진행 상황이 보인다.
 */
class RegisterAccountUseCase @Inject constructor(
    private val discoveryRepository: InstagramBusinessDiscoveryRepository,
    private val watchedAccountRepository: WatchedAccountRepository,
    private val cachedMediaRepository: CachedMediaRepository,
    private val savedPostRepository: SavedPostRepository,
    private val workManager: WorkManager,
    private val tokenStatusStore: TokenStatusStore,
) {
    suspend operator fun invoke(username: String): RegisterAccountResult {
        val cleanUsername = username.removePrefix("@").trim()
        if (cleanUsername.isBlank()) {
            return RegisterAccountResult.Failed("계정 아이디를 입력해주세요")
        }

        val discovered = discoveryRepository.discover(cleanUsername)
        val account = discovered.getOrElse {
            if (it is InstagramTokenExpiredException) tokenStatusStore.markInstagramTokenExpired()
            return RegisterAccountResult.Failed(it.message ?: "계정을 확인할 수 없어요")
        }
        tokenStatusStore.clearInstagramTokenExpired()

        watchedAccountRepository.save(
            WatchedAccountEntity(
                username = account.username,
                igUserId = account.igUserId,
                profileImageUrl = account.profileImageUrl,
                lastSyncedAt = System.currentTimeMillis(),
            )
        )
        cachedMediaRepository.replaceForAccount(account.username, account.media)

        savedPostRepository.findUnresolvedByAccount(account.username).forEach { post ->
            workManager.enqueue(CaptionParsingWorker.request(post.id))
        }

        return RegisterAccountResult.Registered
    }
}
