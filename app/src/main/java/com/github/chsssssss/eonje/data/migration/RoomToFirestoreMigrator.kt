package com.github.chsssssss.eonje.data.migration

import com.github.chsssssss.eonje.data.firestore.BATCH_LIMIT
import com.github.chsssssss.eonje.data.firestore.folders
import com.github.chsssssss.eonje.data.firestore.places
import com.github.chsssssss.eonje.data.firestore.posts
import com.github.chsssssss.eonje.data.firestore.toFields
import com.github.chsssssss.eonje.data.local.ExtractedCandidateDao
import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.data.mapper.toDomain
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import javax.inject.Inject

sealed interface MigrationOutcome {
    /** 이 uid로는 이미 끝나 있었다. 아무것도 쓰지 않았다. */
    data object AlreadyCompleted : MigrationOutcome

    /** Room이 비어 있어서(신규 설치) 옮길 것 없이 바로 완료로 표시했다. */
    data object NothingToMigrate : MigrationOutcome

    /** 처리한 개수는 이어하기 전에 끝낸 것까지 합친 값이다(이미 서버에 있어서 건너뛴 문서 포함). [durationMillis]는 처음 시작부터 끝까지의 벽시계 시간이다. */
    data class Migrated(val folders: Int, val places: Int, val posts: Int, val durationMillis: Long) : MigrationOutcome
}

/** 테스트가 "배치를 커밋한 직후", "체크포인트를 저장한 직후"에 프로세스가 죽는 상황을 만들려고 끼우는 지점. */
interface MigrationHooks {
    suspend fun afterCommit(kind: MigrationKind, page: Int) {}
    suspend fun afterCheckpoint(kind: MigrationKind, page: Int) {}

    companion object {
        val None = object : MigrationHooks {}
    }
}

/**
 * 업데이트 전의 Room 데이터를 Firestore(users/{uid}/…)로 한 번 옮긴다. Room은 읽기만 하고 지우지 않는다.
 *
 * - 순서: 폴더 → 장소 → 게시물. 문서 id는 Room의 UUID 그대로, 필드 변환은 Firestore 저장소가 쓰는 매퍼(`toFields`)를 그대로 쓴다.
 * - 한 배치(최대 [BATCH_LIMIT]개)마다: 서버에 이미 있는 id 확인 → 없는 문서만 쓰기 → **서버 확정까지 기다림** → 체크포인트 저장.
 *   체크포인트는 서버 확정 뒤에만 저장하므로, 도중에 끊겨도 확정된 곳까지만 건너뛰고 이어간다.
 * - 이미 서버에 있는 문서는 절대 덮어쓰지 않는다. 그 사이 사용자가 Firestore 쪽에서 고쳤을 수 있고, "커밋은 됐는데 체크포인트
 *   저장 전에 죽은" 배치가 다시 읽혀도 그 문서는 건너뛰기 때문이다.
 * - 서버를 못 읽거나 확정을 못 받으면 [IOException]을 던진다(호출한 Worker가 백오프로 다시 시도).
 */
class RoomToFirestoreMigrator(
    private val firestore: FirebaseFirestore,
    private val folderDao: FolderDao,
    private val placeDao: PlaceDao,
    private val postDao: SavedPostDao,
    private val crossRefDao: PostPlaceCrossRefDao,
    private val candidateDao: ExtractedCandidateDao,
    private val store: RoomMigrationStateStore,
    private val placesChangedNotifier: PlacesChangedNotifier,
    private val batchSize: Int,
    private val hooks: MigrationHooks,
    private val nowMillis: () -> Long,
) {
    @Inject
    constructor(
        firestore: FirebaseFirestore,
        folderDao: FolderDao,
        placeDao: PlaceDao,
        postDao: SavedPostDao,
        crossRefDao: PostPlaceCrossRefDao,
        candidateDao: ExtractedCandidateDao,
        store: RoomMigrationStateStore,
        placesChangedNotifier: PlacesChangedNotifier,
    ) : this(
        firestore, folderDao, placeDao, postDao, crossRefDao, candidateDao, store, placesChangedNotifier,
        BATCH_LIMIT, MigrationHooks.None, System::currentTimeMillis,
    )

    /** [onStarted]는 이 uid로 새로 시작할 때(이어하기가 아닐 때) 한 번 불린다. */
    suspend fun migrate(uid: String, onStarted: () -> Unit = {}): MigrationOutcome {
        var state = store.read()
        if (state.completed && state.uid == uid) return MigrationOutcome.AlreadyCompleted

        if (state.uid != uid) {
            // 저장된 기록이 없거나 다른 uid의 것(백업 복원 등)이다. 처음부터 하되, 옮길 게 없으면 바로 끝낸다.
            if (isRoomEmpty()) {
                store.markCompleted(uid)
                return MigrationOutcome.NothingToMigrate
            }
            store.begin(uid, nowMillis())
            state = store.read()
            onStarted()
        }

        if (!state.foldersDone) {
            runPhase(uid, MigrationKind.FOLDERS, state.folderCursor, firestore.folders(uid),
                readPage = { after -> folderDao.pageAfter(after, batchSize) },
                idOf = { it.id },
                toFields = { page -> page.associate { it.id to it.toDomain().toFields() } })
        }
        if (!state.placesDone) {
            // 이전 도중에 사용자가 이미 옮겨진 폴더를 지웠을 수 있다. 없는 폴더를 가리키는 folderId는 미분류(null)로 옮긴다.
            val liveFolderIds = readServerIds(firestore.folders(uid))
            runPhase(uid, MigrationKind.PLACES, state.placeCursor, firestore.places(uid),
                readPage = { after -> placeDao.pageAfter(after, batchSize) },
                idOf = { it.id },
                toFields = { page ->
                    page.associate { entity ->
                        val place = entity.toDomain() // memo는 도메인에 없으므로 옮겨지지 않는다
                        entity.id to place.copy(folderId = place.folderId?.takeIf { it in liveFolderIds }).toFields()
                    }
                })
        }
        if (!state.postsDone) {
            runPhase(uid, MigrationKind.POSTS, state.postCursor, firestore.posts(uid),
                readPage = { after -> postDao.pageAfter(after, batchSize) },
                idOf = { it.id },
                toFields = { page ->
                    val ids = page.map { it.id }
                    val placeIds = crossRefDao.forPosts(ids).groupBy({ it.postId }, { it.placeId })
                    val candidates = candidateDao.forPosts(ids).groupBy { it.postId }
                    page.associate { entity ->
                        entity.id to entity.toDomain().toFields(
                            placeIds = placeIds[entity.id].orEmpty().sorted(),
                            candidates = candidates[entity.id].orEmpty().map { it.toDomain() },
                        )
                    }
                })
        }

        store.markCompleted(uid)
        // 이전된 장소가 위젯에도 보이도록 한 번 알린다.
        placesChangedNotifier.notifyPlacesChanged()
        val done = store.read()
        return MigrationOutcome.Migrated(
            folders = done.folderCount,
            places = done.placeCount,
            posts = done.postCount,
            durationMillis = (nowMillis() - done.startedAtMillis).coerceAtLeast(0L),
        )
    }

    private suspend fun isRoomEmpty(): Boolean = folderDao.count() + placeDao.count() + postDao.count() == 0

    /**
     * 한 종류를 커서 다음부터 끝까지 옮긴다. 페이지마다 서버 확정 뒤에 체크포인트를 저장한다.
     * 마지막 페이지(batchSize보다 적게 읽힌 페이지)까지 끝나면 그 종류를 끝난 것으로 표시한다.
     */
    private suspend fun <T> runPhase(
        uid: String,
        kind: MigrationKind,
        startCursor: String,
        collection: CollectionReference,
        readPage: suspend (afterId: String) -> List<T>,
        idOf: (T) -> String,
        toFields: suspend (List<T>) -> Map<String, Map<String, Any?>>,
    ) {
        var cursor = startCursor
        var pageIndex = 0
        while (true) {
            val page = readPage(cursor)
            if (page.isEmpty()) break
            val fieldsById = toFields(page)

            // 서버에 이미 있는 문서는 건너뛴다(덮어쓰지 않는다). 서버에서 직접 읽어야 해서 오프라인이면 여기서 IOException이다.
            val existing = readServerIdsAmong(collection, fieldsById.keys)
            val toWrite = fieldsById.filterKeys { it !in existing }
            if (toWrite.isNotEmpty()) {
                val batch = firestore.batch()
                toWrite.forEach { (id, fields) -> batch.set(collection.document(id), fields) }
                // 백그라운드 Worker라서 서버 확정까지 기다린다. 확정 전엔 체크포인트를 저장하지 않는다.
                // commit()은 Task<Void>라서 정상 완료해도 await()가 null을 돌려준다 — 값이 아니라 true로 완료 여부를 구별한다.
                withTimeoutOrNull(COMMIT_TIMEOUT_MILLIS) { batch.commit().await(); true }
                    ?: throw IOException("서버 확정을 받지 못했다(시간 초과)")
            }
            hooks.afterCommit(kind, pageIndex)

            cursor = idOf(page.last())
            store.saveProgress(uid, kind, cursor, page.size)
            hooks.afterCheckpoint(kind, pageIndex)
            pageIndex++
            if (page.size < batchSize) break
        }
        store.markKindDone(uid, kind)
    }

    /** [ids] 중 서버에 이미 있는 문서 id. whereIn은 값을 30개까지만 받는다. 읽기는 있는 문서만큼(없으면 질의당 1건) 든다. */
    private suspend fun readServerIdsAmong(collection: CollectionReference, ids: Collection<String>): Set<String> {
        val found = HashSet<String>()
        ids.chunked(WHERE_IN_LIMIT).forEach { chunk ->
            val snapshot = withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
                collection.whereIn(FieldPath.documentId(), chunk).get(Source.SERVER).await()
            } ?: throw IOException("서버 읽기 시간 초과")
            snapshot.documents.mapTo(found) { it.id }
        }
        return found
    }

    private suspend fun readServerIds(collection: CollectionReference): Set<String> {
        val snapshot = withTimeoutOrNull(READ_TIMEOUT_MILLIS) { collection.get(Source.SERVER).await() }
            ?: throw IOException("서버 읽기 시간 초과")
        return snapshot.documents.mapTo(HashSet()) { it.id }
    }

    private companion object {
        const val WHERE_IN_LIMIT = 30
        const val COMMIT_TIMEOUT_MILLIS = 60_000L
        const val READ_TIMEOUT_MILLIS = 30_000L
    }
}
