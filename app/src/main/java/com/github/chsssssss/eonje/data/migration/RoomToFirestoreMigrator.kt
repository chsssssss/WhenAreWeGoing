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
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import android.util.Log
import javax.inject.Inject

sealed interface MigrationOutcome {
    /** 이 uid로는 이미 끝나 있었다. 아무것도 쓰지 않았다. */
    data object AlreadyCompleted : MigrationOutcome

    /** Room이 비어 있어서(신규 설치) 옮길 것 없이 바로 완료로 표시했다. */
    data object NothingToMigrate : MigrationOutcome

    /** 처리한 개수는 이어하기 전에 끝낸 것까지 합친 값이다(이미 서버에 있어서 건너뛴 문서 포함). [durationMillis]는 처음 시작부터 끝까지의 벽시계 시간이다. */
    data class Migrated(
        val folders: Int,
        val places: Int,
        val posts: Int,
        val durationMillis: Long,
        /** 규칙 거부 등 영구 실패로 건너뛴 문서 수. 폴더·장소·게시물 개수에는 들어 있지 않다. */
        val skipped: Int = 0,
        /** 규칙 상한을 넘는 값을 고쳐서 옮긴 문서 수(폴더·장소·게시물 개수에는 이미 들어 있다). */
        val sanitized: Int = 0,
    ) : MigrationOutcome
}

/** 테스트가 "배치를 커밋한 직후", "체크포인트를 저장한 직후"에 프로세스가 죽는 상황을 만들려고 끼우는 지점. */
interface MigrationHooks {
    /** 커밋(또는 거부된 배치를 나눠 다시 쓰는 하위 커밋)을 시도하기 직전. 테스트가 일시적 오류를 끼워 넣는 데 쓴다. */
    suspend fun beforeCommit(kind: MigrationKind, ids: List<String>) {}

    /** 프로브 문서를 서버에 쓴 직후, 지우기 전. 테스트가 여기서 프로세스가 죽는 상황을 만든다. */
    suspend fun afterProbeWritten() {}

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
        // 지난번에 프로브 문서를 쓰고 지웠다는 확인을 못 받은 채 끝났다면(앱 종료·시간 초과) 먼저 지운다.
        if (state.uid == uid && state.probePending) {
            deleteProbe(uid)
            state = store.read()
        }
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
                readIds = { after -> folderDao.idsAfter(after, batchSize) },
                readOne = { id -> folderDao.findById(id) },
                idOf = { it.id },
                toFields = { page ->
                    val fixed = page.associate { it.id to LegacyDocSanitizer.folder(it.toDomain()) }
                    PageDocs(fixed.mapValues { it.value.value.toFields() }, fixed.fixedFieldsById())
                })
        }
        if (!state.placesDone) {
            // 이전 도중에 사용자가 이미 옮겨진 폴더를 지웠을 수 있다. 없는 폴더를 가리키는 folderId는 미분류(null)로 옮긴다.
            val liveFolderIds = readServerIds(firestore.folders(uid))
            runPhase(uid, MigrationKind.PLACES, state.placeCursor, firestore.places(uid),
                readPage = { after -> placeDao.pageAfter(after, batchSize) },
                readIds = { after -> placeDao.idsAfter(after, batchSize) },
                readOne = { id -> placeDao.findById(id) },
                idOf = { it.id },
                toFields = { page ->
                    val postIdsByPlace = crossRefDao.forPlaces(page.map { it.id }).groupBy({ it.placeId }, { it.postId })
                    val fixed = page.associate { entity ->
                        val place = entity.toDomain() // memo는 도메인에 없으므로 옮겨지지 않는다
                        entity.id to LegacyDocSanitizer.place(
                            place.copy(
                                folderId = place.folderId?.takeIf { it in liveFolderIds },
                                postIds = postIdsByPlace[entity.id].orEmpty().sorted(),
                            ),
                        )
                    }
                    PageDocs(fixed.mapValues { it.value.value.toFields() }, fixed.fixedFieldsById())
                })
        }
        if (!state.postsDone) {
            runPhase(uid, MigrationKind.POSTS, state.postCursor, firestore.posts(uid),
                readPage = { after -> postDao.pageAfter(after, batchSize) },
                readIds = { after -> postDao.idsAfter(after, batchSize) },
                readOne = { id -> postDao.findById(id) },
                idOf = { it.id },
                toFields = { page ->
                    val ids = page.map { it.id }
                    val placeIds = crossRefDao.forPosts(ids).groupBy({ it.postId }, { it.placeId })
                    val candidates = candidateDao.forPosts(ids).groupBy { it.postId }
                    val fixed = page.associate { entity -> entity.id to LegacyDocSanitizer.post(entity.toDomain()) }
                    PageDocs(
                        fixed.mapValues { (id, sanitized) ->
                            sanitized.value.toFields(
                                placeIds = placeIds[id].orEmpty().sorted(),
                                candidates = candidates[id].orEmpty().map { it.toDomain() },
                            )
                        },
                        fixed.fixedFieldsById(),
                    )
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
            skipped = done.skipped.size,
            sanitized = done.sanitized.size,
        )
    }

    private suspend fun isRoomEmpty(): Boolean = folderDao.count() + placeDao.count() + postDao.count() == 0

    /**
     * 한 종류를 커서 다음부터 끝까지 옮긴다. 페이지마다 서버 확정 뒤에 체크포인트를 저장한다.
     * 마지막 페이지(batchSize보다 적게 읽힌 페이지)까지 끝나면 그 종류를 끝난 것으로 표시한다.
     *
     * **문제 문서는 격리한다.** 규칙이 거부하는 문서(캡션이 상한을 넘는 등)나 Room에서 읽을 수 없는 행 하나 때문에 배치 전체가
     * 막혀 이전이 영영 끝나지 않으면 안 된다. 그런 문서는 건너뛰고 ID·사유만 기록한 채 나머지를 이전한다(Room은 그대로 둔다).
     * 네트워크 오류 같은 일시적 실패는 격리하지 않고 그대로 던진다(Worker가 백오프로 다시 시도).
     */
    private suspend fun <T> runPhase(
        uid: String,
        kind: MigrationKind,
        startCursor: String,
        collection: CollectionReference,
        readPage: suspend (afterId: String) -> List<T>,
        readIds: suspend (afterId: String) -> List<String>,
        readOne: suspend (id: String) -> T?,
        idOf: (T) -> String,
        toFields: suspend (List<T>) -> PageDocs,
    ) {
        var cursor = startCursor
        var pageIndex = 0
        while (true) {
            val read = readPageIsolating(cursor, readPage, readIds, readOne, idOf)
            if (read.lastId == null) break
            val page = read.rows
            val skipped = read.unreadableIds.map { SkippedDoc(it, UNREADABLE) }.toMutableList()
            val docs = if (page.isEmpty()) PageDocs(emptyMap(), emptyMap()) else toFields(page)
            val fieldsById = docs.fields

            // 서버에 이미 있는 문서는 건너뛴다(덮어쓰지 않는다). 서버에서 직접 읽어야 해서 오프라인이면 여기서 IOException이다.
            val existing = if (fieldsById.isEmpty()) emptySet() else readServerIdsAmong(collection, fieldsById.keys)
            val toWrite = fieldsById.filterKeys { it !in existing }.toList()
            if (toWrite.isNotEmpty()) commitIsolating(uid, kind, collection, toWrite, skipped)
            hooks.afterCommit(kind, pageIndex)

            cursor = read.lastId
            // 처리한 개수에는 건너뛴 문서를 넣지 않는다(건너뛴 건 따로 센다). 고쳐서 옮긴 문서는 이번에 실제로 쓴 것 중
            // 끝내 거부되지 않은 것만 센다(이미 서버에 있던 문서는 이전에 센 것이다).
            val skippedIds = skipped.map { it.id }.toSet()
            val sanitized = toWrite.map { it.first }.filter { it !in skippedIds }.mapNotNull { id ->
                docs.sanitized[id]?.let { fields -> "${kind.name}:$id:${fields.joinToString(",")}" }
            }
            sanitized.forEach { Log.i(TAG, "이전에서 규칙 상한에 맞게 고침: $it") }
            store.saveProgress(
                uid, kind, cursor, read.totalRows - skipped.size,
                skipped = skipped.map { skippedEntry(kind, it.id, it.reason) },
                sanitized = sanitized,
            )
            hooks.afterCheckpoint(kind, pageIndex)
            pageIndex++
            if (read.totalRows < batchSize) break
        }
        store.markKindDone(uid, kind)
    }

    /** 쓸 문서(ID → 필드)와, 그중 규칙 상한에 맞게 고친 문서(ID → 고친 필드 이름). */
    private class PageDocs(val fields: Map<String, Map<String, Any?>>, val sanitized: Map<String, List<String>>)

    private class SkippedDoc(val id: String, val reason: String)

    private fun <T> Map<String, Sanitized<T>>.fixedFieldsById(): Map<String, List<String>> =
        filterValues { it.changed }.mapValues { it.value.fixedFields }

    private class PageRead<T>(val rows: List<T>, val unreadableIds: List<String>, val lastId: String?) {
        val totalRows get() = rows.size + unreadableIds.size
    }

    /**
     * Room에서 한 페이지를 읽는다. 행 하나를 읽다 예외가 나면(예: 지금 앱이 모르는 status 문자열 — Converters가 valueOf로 읽다 던진다)
     * 그 페이지 전체를 못 읽게 되므로, id만 먼저 읽고 한 행씩 다시 읽어서 읽을 수 없는 행만 골라낸다.
     */
    private suspend fun <T> readPageIsolating(
        cursor: String,
        readPage: suspend (String) -> List<T>,
        readIds: suspend (String) -> List<String>,
        readOne: suspend (String) -> T?,
        idOf: (T) -> String,
    ): PageRead<T> {
        try {
            val page = readPage(cursor)
            return PageRead(page, emptyList(), page.lastOrNull()?.let(idOf))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 아래 id 읽기까지 실패하면(DB 자체 문제) 그대로 던져서 재시도한다.
            val ids = readIds(cursor)
            val rows = ArrayList<T>()
            val unreadable = ArrayList<String>()
            for (id in ids) {
                try {
                    readOne(id)?.let(rows::add)
                } catch (inner: CancellationException) {
                    throw inner
                } catch (inner: Exception) {
                    unreadable += id
                }
            }
            return PageRead(rows, unreadable, ids.lastOrNull())
        }
    }

    /**
     * [docs]를 한 배치로 커밋한다. 규칙이 거부하면(영구 실패) 반으로 나눠 다시 커밋해서 거부되는 문서를 찾아내고, 문서 하나가
     * 남았는데도 거부되면 그 문서만 [skipped]에 담고 건너뛴다. 정상 문서는 어느 경우에도 한 번만 쓰인다.
     * (499개를 문서 하나씩 순차로 쓰면 서버 왕복이 499번이라, 반씩 나눠서 찾는다.)
     * 일시적 실패(네트워크, 시간 초과 등)는 격리하지 않고 던진다. 이미 커밋된 문서는 다시 시도할 때 존재 확인에서 건너뛴다.
     */
    private suspend fun commitIsolating(
        uid: String,
        kind: MigrationKind,
        collection: CollectionReference,
        docs: List<Pair<String, Map<String, Any?>>>,
        skipped: MutableList<SkippedDoc>,
    ) {
        if (docs.isEmpty()) return
        hooks.beforeCommit(kind, docs.map { it.first })
        try {
            val batch = firestore.batch()
            docs.forEach { (id, fields) -> batch.set(collection.document(id), fields) }
            // 백그라운드 Worker라서 서버 확정까지 기다린다. 확정 전엔 체크포인트를 저장하지 않는다.
            // commit()은 Task<Void>라서 정상 완료해도 await()가 null을 돌려준다 — 값이 아니라 true로 완료 여부를 구별한다.
            withTimeoutOrNull(COMMIT_TIMEOUT_MILLIS) { batch.commit().await(); true }
                ?: throw IOException("서버 확정을 받지 못했다(시간 초과)")
        } catch (e: FirebaseFirestoreException) {
            if (!isPermanentRejection(e)) throw e
            // 문서 하나 때문이 아니라 계정 전체가 거부당하는 상황(규칙 미배포, 로그인 풀림, App Check 시행)이면 격리하지 않고
            // 다시 시도하게 둔다 — 안 그러면 모든 문서를 "건너뜀"으로 처리하고 이전이 완료돼 버린다.
            if (isAccountWideDenial(uid)) throw e
            if (docs.size == 1) {
                val id = docs.single().first
                // ID와 사유 코드만 남긴다. 캡션 같은 내용은 남기지 않는다.
                Log.w(TAG, "이전에서 건너뜀: ${kind.name} $id (${e.code.name})")
                skipped += SkippedDoc(id, e.code.name)
                return
            }
            val half = docs.size / 2
            commitIsolating(uid, kind, collection, docs.subList(0, half), skipped)
            commitIsolating(uid, kind, collection, docs.subList(half, docs.size), skipped)
        }
    }

    // 한 번의 migrate() 안에서 한 번만 확인한다. 계정 전체 거부면 던지고 끝나므로 "아니다"만 캐시하면 된다.
    private var denialIsDocumentSpecific = false

    /**
     * 작은 정상 문서 하나를 쓰고 지워서, 거부가 그 문서 때문인지 계정 전체 때문인지 가른다. 정상 문서마저 거부되면 계정 전체다.
     * 이 프로브는 거부가 처음 나왔을 때만 돈다(쓰기 1 + 삭제 1). 게시물 문서로 쓰되 RESOLVED에 연결이 없어서 화면엔 안 보인다.
     * 정상 문서의 모양은 앱의 매퍼가 만든다 — 규칙이 바뀌어 프로브가 막히면 안전한 쪽(건너뛰지 않고 재시도)으로 기운다.
     */
    private suspend fun isAccountWideDenial(uid: String): Boolean {
        if (denialIsDocumentSpecific) return false
        val ref = firestore.posts(uid).document(PROBE_ID)
        // 쓰기 **전에** 표시를 남긴다 — 쓴 직후 앱이 죽어도 다음 이전 시작 때 이 표시를 보고 문서를 지운다.
        store.setProbePending(uid, true)
        try {
            withTimeoutOrNull(COMMIT_TIMEOUT_MILLIS) { ref.set(probeFields(nowMillis())).await(); true }
                ?: throw IOException("프로브 확정을 받지 못했다(시간 초과)") // 서버에 뒤늦게 들어갈 수 있으니 표시를 남겨 둔다
        } catch (e: FirebaseFirestoreException) {
            if (isPermanentRejection(e)) {
                store.setProbePending(uid, false) // 거부됐으니 문서는 없다
                return true // 정상 문서도 거부된다 → 계정 전체
            }
            throw e
        }
        hooks.afterProbeWritten()
        deleteProbe(uid)
        denialIsDocumentSpecific = true
        return false
    }

    /** 프로브 문서를 지운다. 서버 확정을 받은 뒤에만 표시를 지운다(못 받으면 던져서 다음 시작 때 다시 지운다). */
    private suspend fun deleteProbe(uid: String) {
        withTimeoutOrNull(COMMIT_TIMEOUT_MILLIS) { firestore.posts(uid).document(PROBE_ID).delete().await(); true }
            ?: throw IOException("프로브 삭제 확정을 받지 못했다(시간 초과)")
        store.setProbePending(uid, false)
    }

    /** 규칙 거부(PERMISSION_DENIED)나 문서가 잘못됨(INVALID_ARGUMENT: 크기 초과 등)은 다시 시도해도 같다. 그 밖의 오류는 일시적이다. */
    private fun isPermanentRejection(e: FirebaseFirestoreException): Boolean =
        e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            e.code == FirebaseFirestoreException.Code.INVALID_ARGUMENT

    private fun skippedEntry(kind: MigrationKind, id: String, reason: String) = "${kind.name}:$id:$reason"

    /**
     * [ids] 중 서버에 이미 있는 문서 id. whereIn은 값을 30개까지만 받아서 쪼개 묻는다 — 순차로 하면 배치마다 왕복이 17번이라
     * [PARALLEL_QUERIES]개씩 동시에 보낸다. 읽기 비용은 있는 문서만큼(없으면 질의당 1건)이다.
     */
    private suspend fun readServerIdsAmong(collection: CollectionReference, ids: Collection<String>): Set<String> {
        val found = HashSet<String>()
        ids.chunked(WHERE_IN_LIMIT).chunked(PARALLEL_QUERIES).forEach { window ->
            val results = coroutineScope {
                window.map { chunk ->
                    async {
                        withTimeoutOrNull(READ_TIMEOUT_MILLIS) {
                            collection.whereIn(FieldPath.documentId(), chunk).get(Source.SERVER).await()
                        } ?: throw IOException("서버 읽기 시간 초과")
                    }
                }.awaitAll()
            }
            results.forEach { snapshot -> snapshot.documents.mapTo(found) { it.id } }
        }
        return found
    }

    private suspend fun readServerIds(collection: CollectionReference): Set<String> {
        val snapshot = withTimeoutOrNull(READ_TIMEOUT_MILLIS) { collection.get(Source.SERVER).await() }
            ?: throw IOException("서버 읽기 시간 초과")
        return snapshot.documents.mapTo(HashSet()) { it.id }
    }

    companion object {
        /**
         * 오류 감별용 프로브 문서의 필드. 게시물 문서로 쓰되 RESOLVED에 장소 연결이 없다 - 인박스(미확정만), 홈·지도·위젯(장소 컬렉션과
         * 장소별 게시물 묶음)에 안 보인다. 앱의 매퍼로 만들므로 규칙 테스트가 이 문서를 그대로 써서 규칙을 통과하는지 확인한다.
         */
        internal fun probeFields(nowMillis: Long): Map<String, Any?> = SavedPost(
            id = PROBE_ID, shortcode = null, instagramUrl = "https://www.instagram.com/p/$PROBE_ID/", caption = null,
            thumbnailUrl = null, status = ResolveStatus.RESOLVED, extractedCount = 0, createdAt = nowMillis,
        ).toFields()

        internal const val PROBE_ID = "migration-probe"

        private const val TAG = "EonjeMigration"
        const val UNREADABLE = "UNREADABLE"
        const val WHERE_IN_LIMIT = 30
        const val PARALLEL_QUERIES = 8
        const val COMMIT_TIMEOUT_MILLIS = 60_000L
        const val READ_TIMEOUT_MILLIS = 30_000L
    }
}
