package com.github.chsssssss.eonje.data.local

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 기존 썸네일 1회 정리를 이미 했는지. 앱 시작마다 도는 고아 정리와 달리 한 번만 한다. */
interface ThumbnailMaintenanceState {
    suspend fun isCompactionDone(): Boolean
    suspend fun markCompactionDone()
}

/**
 * DataStore에 저장한다 — Room 스키마를 바꾸지 않으려고 DB가 아니라 별도 파일을 쓴다.
 * 같은 파일에 DataStore 인스턴스가 둘 생기면 안 되므로 @Singleton이다.
 */
@Singleton
class ThumbnailMaintenanceStateImpl @Inject constructor(
    @ApplicationContext context: Context,
) : ThumbnailMaintenanceState {
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
    )

    // 읽지 못하면 "아직 안 했다"로 본다 — 정리는 이미 작은 파일을 건드리지 않아서 다시 돌아도 해가 없다.
    override suspend fun isCompactionDone(): Boolean = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .first()[KEY_COMPACTION_DONE] ?: false

    override suspend fun markCompactionDone() {
        dataStore.edit { it[KEY_COMPACTION_DONE] = true }
    }

    private companion object {
        const val DATASTORE_NAME = "thumbnail_maintenance"
        val KEY_COMPACTION_DONE = booleanPreferencesKey("compaction_v1_done")
    }
}

/** 1회 정리 결과. [processedFiles]는 줄였거나 지운 파일 수, [savedBytes]는 그만큼 줄어든 용량. */
data class CompactionReport(val processedFiles: Int, val savedBytes: Long)

/** [compaction]은 이번 실행에서 1회 정리를 했을 때만 있다. */
data class MaintenanceReport(val orphansDeleted: Int, val compaction: CompactionReport?)

/**
 * 썸네일 폴더 정리 두 가지(저장 파이프라인은 건드리지 않는다).
 *
 * 1. 고아 파일 정리(매 실행): DB의 어떤 게시물과도 맞지 않는 파일을 지운다. 게시물 행은 지우지 않는다.
 * 2. 기존 파일 1회 정리: 업데이트 전에 원본 크기로 쌓인 파일을 새 기준으로 줄인다.
 */
class ThumbnailMaintenance @Inject constructor(
    private val store: ThumbnailStore,
    private val posts: SavedPostRepository,
    private val state: ThumbnailMaintenanceState,
    private val legacyMigration: LegacyMigrationStatus,
) {
    suspend fun run(nowMillis: Long): MaintenanceReport = withContext(Dispatchers.IO) {
        // 순서가 중요하다: 파일 목록을 먼저 만들고 그다음에 DB의 게시물을 읽는다.
        // 게시물 행은 파일보다 먼저 생기므로, 목록에 있는 파일의 게시물은 항상 이후 읽기에 들어 있다.
        // 반대로 하면 그 사이에 새로 저장된 게시물의 파일을 고아로 잘못 지울 수 있다.
        val files = store.snapshotFiles()
        // 서버를 읽지 않는 로컬 ID만 쓴다(게시물이 늘수록 앱을 열 때마다 읽기 비용이 드는 걸 피한다).
        val knownIds = posts.cachedPostIds()

        // 로컬 데이터가 비어 있으면 "게시물이 없다"와 "아직 로컬에 못 올라왔다"를 구별할 수 없다. 썸네일은 다시 받을 수 없어서
        // 이때는 파일 이름으로 고아를 판단하지 않고, 오래된 .tmp만 정리한다.
        //
        // Room → Firestore 이전이 안 끝났을 때도 마찬가지다: 일부만 옮겨진 상태에서 ID 목록에 없다고 지우면, 아직 안 옮겨진 게시물의
        // 썸네일(다시 받을 수 없다)을 잃는다. 이전이 끝난 뒤에만 고아를 판단한다.
        val canJudgeOrphans = knownIds.isNotEmpty() && legacyMigration.isComplete()
        val orphanCandidates = if (canJudgeOrphans) files else files.filter { it.name.endsWith(".tmp") }

        // 캐시에 없다고 곧바로 고아는 아니다: Firestore 캐시는 20MB 상한으로 정리(GC)되거나 일부만 채워져 있을 수 있고,
        // 썸네일은 지우면 복구할 수 없다. 캐시 기준으로 의심되는 파일만 서버에 물어서, **서버에도 없다고 확인된 것만** 지운다.
        // 서버가 대답하지 않은 것(오프라인·오류)과 한 번에 확인할 수 있는 개수를 넘는 것은 지키고 다음 기회로 미룬다.
        // 읽기는 의심 파일 수만큼만(최대 MAX_SERVER_CHECKS_PER_RUN개) 들고, 의심 파일이 없으면 0이다.
        // Room → Firestore 이전에서 건너뛴 게시물(규칙이 거부한 문서)은 서버에 문서가 없어도 고아가 아니다 — Room에 남아 있고 썸네일은
        // 복구할 수 없다. 서버에 묻지도 않고 지키기만 한다.
        val skippedKeys = if (canJudgeOrphans) legacyMigration.skippedPostIds() else emptySet()
        val keysToKeep = if (canJudgeOrphans) {
            val suspectKeys = files.filter { !it.name.endsWith(".tmp") }.map(store::keyOf)
                .filter { it !in knownIds && it !in skippedKeys }.distinct()
            val confirmedAbsent = if (suspectKeys.isEmpty()) emptySet() else posts.findIdsAbsentOnServer(suspectKeys.take(MAX_SERVER_CHECKS_PER_RUN))
            suspectKeys.toSet() - confirmedAbsent + skippedKeys
        } else {
            emptySet()
        }
        val orphansDeleted = store.deleteOrphans(orphanCandidates, knownIds + keysToKeep, nowMillis)
        if (state.isCompactionDone()) return@withContext MaintenanceReport(orphansDeleted, null)

        val targets = files.filter { it.name.endsWith(JPG_SUFFIX) && it.exists() }
        val report = compactAll(targets)
        // 파일 하나가 실패해도 계속 진행한 결과다. 한 번만 하기로 했으므로 여기서 완료로 표시한다.
        state.markCompactionDone()
        MaintenanceReport(orphansDeleted, report)
    }

    private suspend fun compactAll(files: List<File>): CompactionReport {
        var processed = 0
        var saved = 0L
        for (file in files) {
            currentCoroutineContext().ensureActive() // 작업이 취소되면 다음 파일로 넘어가지 않는다
            when (val result = store.compact(file)) {
                is CompactResult.Replaced -> {
                    processed++
                    saved += result.bytesBefore - result.bytesAfter
                }
                is CompactResult.Corrupt -> {
                    processed++
                    saved += result.freedBytes
                    // 지워진 파일을 가리키는 경로를 비운다. 이미 없는 파일을 가리키는 것보다 "사진 없음"이 정확하다.
                    try {
                        posts.clearLocalThumbnail(store.keyOf(file))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 다음 파일로 계속 진행한다.
                    }
                }
                CompactResult.Skipped, CompactResult.Failed -> Unit
            }
        }
        return CompactionReport(processed, saved)
    }

    private companion object {
        const val JPG_SUFFIX = ".jpg"

        /** 한 번에 서버에 확인하는 의심 파일의 상한(whereIn 30개 × 2번). 나머지는 다음 실행에서 본다. */
        const val MAX_SERVER_CHECKS_PER_RUN = 60
    }
}
