package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.github.chsssssss.eonje.data.local.TokenStatusStore
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
import com.github.chsssssss.eonje.domain.repository.InstagramTokenExpiredException
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
import com.github.chsssssss.eonje.domain.usecase.MatchPostUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/** F3: 등록된 계정을 1일 1회 동기화하고, 그 계정의 UNRESOLVED 게시물을 재매칭한다. */
@HiltWorker
class BusinessDiscoverySyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val watchedAccountRepository: WatchedAccountRepository,
    private val discoveryRepository: InstagramBusinessDiscoveryRepository,
    private val cachedMediaRepository: CachedMediaRepository,
    private val savedPostRepository: SavedPostRepository,
    private val matchPostUseCase: MatchPostUseCase,
    private val tokenStatusStore: TokenStatusStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        for (account in watchedAccountRepository.getAll()) {
            val result = discoveryRepository.discover(account.username)
            val error = result.exceptionOrNull()

            if (error is InstagramTokenExpiredException) {
                // 토큰 만료 → 앱 내 배너, 동기화 중단. 나머지 계정도 같은 토큰을 쓰므로 이번 실행은 여기서 멈춘다.
                watchedAccountRepository.markSyncFailed(account.username, "토큰 만료 · 설정에서 확인해주세요")
                tokenStatusStore.markInstagramTokenExpired()
                break
            }

            val discovered = result.getOrNull()
            if (discovered == null) {
                watchedAccountRepository.markSyncFailed(account.username, "동기화 실패")
                continue
            }

            tokenStatusStore.clearInstagramTokenExpired()
            cachedMediaRepository.replaceForAccount(account.username, discovered.media)
            watchedAccountRepository.markSynced(account.username)

            savedPostRepository.findUnresolvedByAccount(account.username).forEach { post ->
                matchPostUseCase(post.id)
            }
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "business_discovery_sync"
        val EXISTING_POLICY = ExistingPeriodicWorkPolicy.KEEP

        fun periodicRequest() =
            PeriodicWorkRequestBuilder<BusinessDiscoverySyncWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresCharging(true)
                        .build()
                )
                .build()
    }
}
