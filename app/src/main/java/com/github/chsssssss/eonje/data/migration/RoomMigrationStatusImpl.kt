package com.github.chsssssss.eonje.data.migration

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.data.worker.RoomMigrationWorker
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.UserSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 이 uid로 이전이 끝났는지(DataStore)와 화면용 이전 상태. 새 서버 호출은 없다 — DataStore, Room 개수(로컬), Worker 상태만 본다.
 *
 * [migrationWorkRunning]은 이전 Worker가 지금 실행 중인지의 흐름이다(운영에선 WorkManager, 테스트에선 가짜).
 */
@Singleton
class RoomMigrationStatusImpl(
    private val store: RoomMigrationStateStore,
    private val session: UserSession,
    private val folderDao: FolderDao,
    private val placeDao: PlaceDao,
    private val postDao: SavedPostDao,
    private val migrationWorkRunning: Flow<Boolean>,
) : LegacyMigrationStatus {

    @Inject
    constructor(
        store: RoomMigrationStateStore,
        session: UserSession,
        folderDao: FolderDao,
        placeDao: PlaceDao,
        postDao: SavedPostDao,
        workManager: WorkManager,
    ) : this(
        store, session, folderDao, placeDao, postDao,
        workManager.getWorkInfosForUniqueWorkFlow(RoomMigrationWorker.WORK_NAME)
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING } },
    )

    // Room은 이전 중에도 이후에도 쓰이지 않으므로(저장소가 전부 Firestore) 한 번 읽은 "데이터 있음/없음"은 그대로다.
    @Volatile
    private var roomHasData: Boolean? = null

    private suspend fun hasRoomData(): Boolean = roomHasData
        ?: (folderDao.count() + placeDao.count() + postDao.count() > 0).also { roomHasData = it }

    override suspend fun isComplete(): Boolean {
        val uid = session.currentUid() ?: return false
        val state = store.read()
        // 영구 종료 뒤엔 Room의 옛 데이터는 더 이상 "이전 대기 중인 원본"이 아니다(중복 판단·고아 정리에서 완료로 본다).
        return state.closed || (state.completed && state.uid == uid)
    }

    override suspend fun isLegacyDuplicate(shortcode: String): Boolean {
        if (isComplete()) return false
        return postDao.findByShortcode(shortcode) != null
    }

    override suspend fun skippedPostIds(): Set<String> {
        val state = store.read()
        return if (state.uid == session.currentUid()) state.skippedPostIds else emptySet()
    }

    override fun observePhase(): Flow<LegacyMigrationPhase> =
        combine(session.uid, store.states, migrationWorkRunning) { uid, state, running ->
            legacyMigrationPhase(uid, state, running, ::hasRoomData)
        }.distinctUntilChanged()
}

/**
 * 화면용 이전 상태를 정한다.
 * - 영구 종료 플래그가 켜져 있으면(로그아웃·계정 삭제·전환 뒤) → 없음.
 * - 이 uid로 끝났으면 → 없음. (Room은 읽지 않는다)
 * - Room이 비어 있으면(신규 설치) → 없음. 신규 설치 사용자에겐 아무 영향이 없다.
 * - 이전 대상이 있는데 로그인 전이면(uid 없음) → 대기(Worker가 로그인을 기다리는 중이라도 아직 못 옮기고 있다).
 * - Worker가 실행 중이면 → 이전 중, 아니면(오프라인·재시도 대기·아직 예약 전) → 대기.
 */
internal suspend fun legacyMigrationPhase(
    uid: String?,
    state: MigrationState,
    workerRunning: Boolean,
    hasRoomData: suspend () -> Boolean,
): LegacyMigrationPhase = when {
    state.closed -> LegacyMigrationPhase.NOT_NEEDED
    uid != null && state.completed && state.uid == uid -> LegacyMigrationPhase.NOT_NEEDED
    !hasRoomData() -> LegacyMigrationPhase.NOT_NEEDED
    uid == null -> LegacyMigrationPhase.WAITING
    workerRunning -> LegacyMigrationPhase.MIGRATING
    else -> LegacyMigrationPhase.WAITING
}
