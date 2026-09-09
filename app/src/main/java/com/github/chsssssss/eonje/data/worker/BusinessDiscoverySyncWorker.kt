package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
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
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        watchedAccountRepository.getAll().forEach { account ->
            val discovered = discoveryRepository.discover(account.username).getOrNull()
            if (discovered == null) {
                watchedAccountRepository.markSyncFailed(account.username, "동기화 실패")
                return@forEach
            }

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
