package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.data.local.WatchedAccountEntity
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
import javax.inject.Inject

sealed interface RegisterAccountResult {
    data object Registered : RegisterAccountResult
    data class Failed(val message: String) : RegisterAccountResult
}

/** F3: username → 프로페셔널 계정 확인(Business Discovery) → 등록 → 해당 계정 UNRESOLVED 게시물 재매칭. */
class RegisterAccountUseCase @Inject constructor(
    private val discoveryRepository: InstagramBusinessDiscoveryRepository,
    private val watchedAccountRepository: WatchedAccountRepository,
    private val cachedMediaRepository: CachedMediaRepository,
    private val savedPostRepository: SavedPostRepository,
    private val matchPostUseCase: MatchPostUseCase,
) {
    suspend operator fun invoke(username: String): RegisterAccountResult {
        val cleanUsername = username.removePrefix("@").trim()
        if (cleanUsername.isBlank()) {
            return RegisterAccountResult.Failed("계정 아이디를 입력해주세요")
        }

        val account = discoveryRepository.discover(cleanUsername).getOrElse {
            return RegisterAccountResult.Failed(it.message ?: "계정을 확인할 수 없어요")
        }

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
            matchPostUseCase(post.id)
        }

        return RegisterAccountResult.Registered
    }
}
