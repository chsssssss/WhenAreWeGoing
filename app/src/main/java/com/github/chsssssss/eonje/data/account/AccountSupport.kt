package com.github.chsssssss.eonje.data.account

import android.content.Context
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.firestore.PlaceSyncStore
import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.data.worker.CaptionParsingWorker
import com.github.chsssssss.eonje.data.worker.RoomMigrationWorker
import com.github.chsssssss.eonje.data.worker.ThumbnailMaintenanceWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 계정 삭제 중에는 백그라운드 작업이 새 문서를 쓰지 않게 한다. 공유 저장 Worker는 이 값이 켜져 있으면 다시 시도로 미룬다
 * (URL이 사라지면 안 되므로 취소하지 않는다). 캡션 파싱·Room 이전은 삭제 시작 때 취소한다.
 */
@Singleton
class AccountOperationGate @Inject constructor() {
    @Volatile
    var isActive: Boolean = false
}

interface AccountWorkControl {
    /** 삭제 시작 전에: 공유 저장은 대기시키고, 캡션 파싱·Room 이전은 멈춘다. */
    fun pause()

    /** 삭제가 끝나면(성공이든 실패든) 다시 허용한다. */
    fun resume()
}

@Singleton
class WorkManagerAccountWorkControl @Inject constructor(
    private val workManager: WorkManager,
    private val gate: AccountOperationGate,
) : AccountWorkControl {
    override fun pause() {
        gate.isActive = true
        workManager.cancelAllWorkByTag(CaptionParsingWorker.TAG)
        workManager.cancelUniqueWork(RoomMigrationWorker.WORK_NAME)
    }

    override fun resume() {
        gate.isActive = false
    }
}

/** 로그아웃·삭제 뒤 이 기기에서 정리할 것들. 테스트에선 가짜로 바꾼다. */
interface AccountLocalCleanup {
    /** 내려받아 둔 썸네일 파일과 장소 동기화 기록(uid 기준 캐시 판단용)을 지운다. */
    suspend fun clearDeviceCaches()

    /** Room 테이블의 데이터를 비운다(스키마는 그대로). **사용자가 직접 요청한 삭제에서만** 쓴다. */
    suspend fun clearLegacyRoom()

    /** 다음 앱 콜드 스타트에 Firestore 로컬 캐시를 지우도록 표시한다(실행 중엔 지울 수 없다). */
    fun requestFirestoreCacheClear()

    /** 합치기 저널이 사라진 뒤 썸네일 고아 정리를 다시 돌린다(저널이 있는 동안엔 건너뛰었다). */
    fun scheduleThumbnailMaintenance()
}

@Singleton
class AccountLocalCleanupImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val thumbnailStore: ThumbnailStore,
    private val placeSyncStore: PlaceSyncStore,
    private val database: EonjeDatabase,
    private val workManager: WorkManager,
) : AccountLocalCleanup {
    override suspend fun clearDeviceCaches() = withContext(Dispatchers.IO) {
        thumbnailStore.clearAll()
        placeSyncStore.clear()
    }

    override suspend fun clearLegacyRoom() = withContext(Dispatchers.IO) { database.clearAllTables() }

    override fun requestFirestoreCacheClear() = FirestoreCacheClearFlag.request(context)

    override fun scheduleThumbnailMaintenance() = ThumbnailMaintenanceWorker.enqueue(workManager)
}

/** SharedPreferences라서 Application.onCreate(Firestore를 쓰기 전)에서 동기로 읽을 수 있다. */
object FirestoreCacheClearFlag {
    private const val PREFS = "account_local_state"
    private const val KEY = "clear_firestore_cache_on_start"

    fun request(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }

    fun isRequested(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
