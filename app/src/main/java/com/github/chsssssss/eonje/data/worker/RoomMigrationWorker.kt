package com.github.chsssssss.eonje.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.data.migration.MigrationOutcome
import com.github.chsssssss.eonje.data.migration.RoomToFirestoreMigrator
import com.github.chsssssss.eonje.domain.repository.UserSession
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 업데이트를 받은 기존 사용자의 Room 데이터를 Firestore로 한 번 옮긴다([RoomToFirestoreMigrator]).
 * 앱을 시작할 때 예약하고, 로그인은 직접 시도한다(AuthSession 재사용). 네트워크 제약이 있어서 오프라인 동안엔 시도 횟수를 쓰지 않고
 * 기다리며, 실패하면 지수 백오프로 계속 다시 시도한다 — 포기하면 사용자는 옛 데이터를 못 보게 되므로 상한을 두지 않는다.
 * 이 uid로 이미 끝났으면 DataStore 한 번 읽고 바로 끝나서 매번 예약해도 싸다.
 */
@HiltWorker
class RoomMigrationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val userSession: UserSession,
    private val migrator: RoomToFirestoreMigrator,
    private val analytics: FirebaseAnalytics,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        if (!userSession.signInIfNeeded()) return Result.retry()
        val uid = userSession.currentUid() ?: return Result.retry()

        return try {
            val outcome = migrator.migrate(uid, onStarted = { analytics.logEvent(Events.ROOM_MIGRATION_STARTED, null) })
            if (outcome is MigrationOutcome.Migrated) {
                analytics.logEvent(Events.ROOM_MIGRATION_COMPLETED) {
                    param(Events.Param.FOLDER_COUNT, outcome.folders.toLong())
                    param(Events.Param.PLACE_COUNT, outcome.places.toLong())
                    param(Events.Param.POST_COUNT, outcome.posts.toLong())
                    param(Events.Param.SKIPPED_COUNT, outcome.skipped.toLong())
                    param(Events.Param.SANITIZED_COUNT, outcome.sanitized.toLong())
                    param(Events.Param.DURATION_MS, outcome.durationMillis)
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            analytics.logEvent(Events.ROOM_MIGRATION_FAILED) { param(Events.Param.ERROR_TYPE, errorTypeOf(e)) }
            Result.retry()
        }
    }

    private fun errorTypeOf(e: Exception): String = when {
        e is IOException -> Events.MigrationError.NETWORK
        e is FirebaseFirestoreException && e.code in NETWORK_CODES -> Events.MigrationError.NETWORK
        e is FirebaseFirestoreException && e.code in PERMISSION_CODES -> Events.MigrationError.PERMISSION
        else -> Events.MigrationError.UNKNOWN
    }

    companion object {
        const val WORK_NAME = "room_migration"

        private val NETWORK_CODES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.CANCELLED,
        )
        private val PERMISSION_CODES = setOf(
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED,
        )

        /**
         * 앱 시작 때마다 예약한다. REPLACE라서 백오프 대기 중이던 이전 시도를 끊고 바로 다시 시작한다 — 이어하기는 체크포인트가
         * 맡으므로 도중에 끊어도 안전하다.
         */
        fun enqueue(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<RoomMigrationWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
