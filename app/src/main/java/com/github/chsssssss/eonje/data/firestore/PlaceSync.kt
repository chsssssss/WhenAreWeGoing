package com.github.chsssssss.eonje.data.firestore

import android.content.Context
import android.util.Log
import com.github.chsssssss.eonje.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.DocumentSnapshot.ServerTimestampBehavior
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** 묘비를 남겨두는 기간. 이 기간이 지나면 Firestore TTL 정책(`expireAt`)이 문서를 지운다. */
internal const val TOMBSTONE_RETENTION_MILLIS = 90L * 24 * 60 * 60 * 1000

/**
 * 장소 동기화 판단(순수 함수). 장소는 홈에서 캐시로만 그리고, 서버에는 "마지막으로 받은 시점 이후 바뀐 것"만 묻는다.
 *
 * 삭제는 묘비(deleted=true)로 알리고 묘비는 [TOMBSTONE_RETENTION_MILLIS] 뒤에 사라진다. 그보다 오래 동기화하지 않은 기기는
 * 삭제 소식을 놓쳤을 수 있어서 처음부터 다시 받는다. 보관 기간보다 짧게(10일 여유) 잡는다.
 */
internal object PlaceSyncPolicy {
    const val FULL_SYNC_MAX_AGE_MILLIS = TOMBSTONE_RETENTION_MILLIS - 10L * 24 * 60 * 60 * 1000

    /** 기기 시계와 서버 시계, 쓰기 순서의 오차를 덮는 여유. 겹쳐 받는 문서는 몇 건뿐이다. */
    const val CURSOR_MARGIN_MILLIS = 2L * 60 * 1000

    /**
     * 전체를 받아야 하나: 한 번도 안 받았거나(새 설치·새 기기·다른 uid), 캐시에 장소가 하나도 없거나(기록은 있는데 캐시가 비었다 —
     * 백업 복원 등), 너무 오래됐거나, 시계가 거꾸로 갔을 때.
     */
    fun needsFullSync(lastFullSyncMillis: Long?, cacheHasPlaces: Boolean, nowMillis: Long): Boolean =
        lastFullSyncMillis == null ||
            !cacheHasPlaces ||
            nowMillis < lastFullSyncMillis ||
            nowMillis - lastFullSyncMillis > FULL_SYNC_MAX_AGE_MILLIS

    /** 델타 질의의 기준 시각: 캐시에서 서버가 확정한 최신 updatedAt(없으면 마지막 전체 동기화 시각)에서 여유를 뺀다. */
    fun deltaCursorMillis(maxCachedUpdatedMillis: Long?, lastFullSyncMillis: Long): Long =
        (maxCachedUpdatedMillis ?: lastFullSyncMillis) - CURSOR_MARGIN_MILLIS
}

/** 마지막 전체 동기화 시각을 uid와 함께 기억한다. uid가 다르면 없는 것으로 본다(계정이 바뀌었거나 백업이 복원된 경우). */
interface PlaceSyncStore {
    fun lastFullSyncMillis(uid: String): Long?
    fun markFullSync(uid: String, millis: Long)

    /** 기록을 모두 지운다(로그아웃·계정 삭제 뒤). */
    fun clear()
}

class InMemoryPlaceSyncStore : PlaceSyncStore {
    private val map = ConcurrentHashMap<String, Long>()
    override fun lastFullSyncMillis(uid: String): Long? = map[uid]
    override fun markFullSync(uid: String, millis: Long) { map[uid] = millis }
    override fun clear() = map.clear()
}

@Singleton
class SharedPrefsPlaceSyncStore @Inject constructor(@ApplicationContext context: Context) : PlaceSyncStore {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun lastFullSyncMillis(uid: String): Long? =
        if (prefs.getString(KEY_UID, null) == uid && prefs.contains(KEY_LAST_FULL_SYNC)) prefs.getLong(KEY_LAST_FULL_SYNC, 0L) else null

    override fun markFullSync(uid: String, millis: Long) {
        prefs.edit().putString(KEY_UID, uid).putLong(KEY_LAST_FULL_SYNC, millis).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        const val PREFS_NAME = "place_sync"
        private const val KEY_UID = "uid"
        private const val KEY_LAST_FULL_SYNC = "last_full_sync_millis"
    }
}

/**
 * 장소 컬렉션을 서버와 맞춰서 로컬 캐시를 최신으로 유지한다. 화면은 이걸 직접 듣지 않고 캐시 전용 리스너로 그린다.
 *
 * 1. 처음이거나 오래됐으면 장소 전체를 서버에서 한 번 받고(읽기 = 장소 수), 서버에 없는데 캐시에 남은 문서는 지운다.
 * 2. 그다음엔 `updatedAt > 기준 시각`인 장소만 듣는다(읽기 = 바뀐 문서 수, 없으면 1). 홈이 보이는 동안만 돌고
 *    수집이 끝나면 리스너가 해제된다.
 */
@Singleton
class PlaceSync @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val store: PlaceSyncStore,
) {
    suspend fun run(uid: String, nowMillis: () -> Long = System::currentTimeMillis) {
        try {
            val places = firestore.places(uid)
            val cachedBefore = readCache(places)
            val now = nowMillis()
            var lastFull = store.lastFullSyncMillis(uid)
            if (PlaceSyncPolicy.needsFullSync(lastFull, cachedBefore.isNotEmpty(), now)) {
                if (!fullSync(uid, places, cachedBefore, now)) return // 오프라인 등: 캐시로 그리고, 다음에 다시 시도한다
                lastFull = now
            }
            val cursor = PlaceSyncPolicy.deltaCursorMillis(maxServerUpdatedMillis(readCache(places)), lastFull ?: now)
            places.whereGreaterThan(Fields.UPDATED_AT, Timestamp(Date(cursor))).snapshots().collect { snapshot ->
                // 디버그 빌드에서만, 서버가 보낸 문서 수(개수만)를 남긴다. 델타 동기화가 읽기를 줄이는지 확인하는 용도.
                if (BuildConfig.DEBUG && !snapshot.metadata.isFromCache) Log.d(TAG, "장소 델타 ${snapshot.documentChanges.size}건")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logListenerError("장소 동기화", e)
        }
    }

    private suspend fun fullSync(uid: String, places: Query, cached: List<DocumentSnapshot>, nowMillis: Long): Boolean {
        val server = try {
            withTimeout(FULL_SYNC_TIMEOUT_MILLIS) { places.get(Source.SERVER).await() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return false
        }
        if (BuildConfig.DEBUG) Log.d(TAG, "장소 전체 동기화 ${server.size()}건")
        // 서버에 없는데 캐시에 남은 문서(묘비가 사라진 뒤에야 돌아온 기기)는 지워서 화면에서 없앤다. 서버엔 이미 없어서 삭제는 변화가
        // 없다. 아직 서버에 안 올라간 내 쓰기(pending)는 건드리지 않는다.
        val serverIds = server.documents.mapTo(HashSet()) { it.id }
        val stale = cached.filter { it.id !in serverIds && !it.metadata.hasPendingWrites() }
        if (stale.isNotEmpty()) {
            firestore.commitInChunks("장소 캐시 정리", stale.map { doc -> { batch -> batch.delete(doc.reference) } })
        }
        store.markFullSync(uid, nowMillis)
        return true
    }

    private suspend fun readCache(places: Query): List<DocumentSnapshot> = try {
        places.get(Source.CACHE).await().documents
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emptyList()
    }

    /** 서버가 확정한 updatedAt의 최댓값. 아직 올라가지 않은 내 쓰기(pending)는 시각이 추정값이라 뺀다. */
    private fun maxServerUpdatedMillis(docs: List<DocumentSnapshot>): Long? =
        docs.filter { !it.metadata.hasPendingWrites() }
            .mapNotNull { it.getTimestamp(Fields.UPDATED_AT, ServerTimestampBehavior.NONE)?.toDate()?.time }
            .maxOrNull()

    private companion object {
        const val TAG = "EonjeFirestore"
        const val FULL_SYNC_TIMEOUT_MILLIS = 30_000L
    }
}
