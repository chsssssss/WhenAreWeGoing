package com.github.chsssssss.eonje.data.firestore

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenSource
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SnapshotListenOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private const val TAG = "EonjeFirestore"

/** 캐시에 없어서 서버에 물어볼 때 기다리는 최대 시간. 오프라인이면 SDK가 곧바로 실패해서 거의 안 기다린다. */
internal const val SERVER_FALLBACK_TIMEOUT_MILLIS = 2_500L

/** WriteBatch 한 번에 넣을 수 있는 연산은 500개다. 여유를 둔다. */
internal const val BATCH_LIMIT = 499

// ── 컬렉션 경로: 개인 데이터는 전부 users/{uid} 아래 ──
internal fun FirebaseFirestore.userDoc(uid: String): DocumentReference = collection("users").document(uid)
internal fun FirebaseFirestore.posts(uid: String): CollectionReference = userDoc(uid).collection("posts")
internal fun FirebaseFirestore.places(uid: String): CollectionReference = userDoc(uid).collection("places")
internal fun FirebaseFirestore.folders(uid: String): CollectionReference = userDoc(uid).collection("folders")

/**
 * 변경이 생길 때마다 내보내는 리스너 Flow. 로컬 캐시의 첫 스냅샷이 먼저 오고(오프라인에서도), 내가 쓴 것이 서버에 올라가기 전에도
 * 로컬 반영분이 바로 포함된다. 수집이 끝나면 리스너를 해제한다.
 */
internal fun Query.snapshots(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * 로컬 캐시만 듣는 리스너 Flow(서버를 읽지 않아 과금이 없다). 다른 리스너(델타 동기화)가 캐시를 채우거나 내가 쓰면 그 변화가 반영된다.
 * 캐시가 비어 있으면 빈 결과를 낸다 — 캐시를 채우는 건 [PlaceSync]의 몫이다.
 */
internal fun Query.cacheSnapshots(): Flow<QuerySnapshot> = callbackFlow {
    val options = SnapshotListenOptions.Builder().setSource(ListenSource.CACHE).build()
    val registration = addSnapshotListener(options) { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

internal fun DocumentReference.snapshots(): Flow<DocumentSnapshot> = callbackFlow {
    val registration = addSnapshotListener { snapshot, error ->
        if (error != null) close(error) else if (snapshot != null) trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

/**
 * 로컬 캐시를 먼저 보고, 비어 있을 때만 서버에 묻는다(제한 시간 있음). 서버 읽기 비용을 아끼고 오프라인에서도 멈추지 않는다.
 * 서버도 못 읽으면 캐시 결과(비어 있을 수 있음)를, 캐시 읽기조차 안 되면 null을 준다.
 */
internal suspend fun Query.getCacheFirst(timeoutMillis: Long = SERVER_FALLBACK_TIMEOUT_MILLIS): QuerySnapshot? {
    val cached = attempt { get(Source.CACHE).await() }
    if (cached != null && !cached.isEmpty) return cached
    return attempt { withTimeout(timeoutMillis) { get(Source.SERVER).await() } } ?: cached
}

internal suspend fun DocumentReference.getCacheFirst(timeoutMillis: Long = SERVER_FALLBACK_TIMEOUT_MILLIS): DocumentSnapshot? {
    val cached = attempt { get(Source.CACHE).await() }
    if (cached != null && cached.exists()) return cached
    return attempt { withTimeout(timeoutMillis) { get(Source.SERVER).await() } } ?: cached
}

/** 실패(오프라인, 캐시 없음, 제한 시간)는 null로 바꾸되, 코루틴 취소는 삼키지 않는다. */
private suspend inline fun <T> attempt(block: () -> T): T? = try {
    block()
} catch (e: TimeoutCancellationException) {
    null
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

/**
 * 쓰기 Task는 서버 응답이 와야 끝나서 오프라인에선 끝나지 않는다 — 기다리지 않고 실패만 기록한다.
 * 로그엔 작업 이름과 오류 종류만 남긴다(문서 경로·내용은 남기지 않는다).
 */
internal fun Task<*>.logFailure(operation: String) {
    addOnFailureListener { e ->
        val code = (e as? FirebaseFirestoreException)?.code?.name ?: e.javaClass.simpleName
        Log.w(TAG, "$operation 실패: $code")
    }
}

/** 연산이 많으면 [BATCH_LIMIT]개씩 나눠서 커밋한다. 커밋은 호출 순서대로 처리되고, 서버 응답은 기다리지 않는다. */
internal fun FirebaseFirestore.commitInChunks(operation: String, ops: List<(WriteBatch) -> Unit>) {
    ops.chunked(BATCH_LIMIT).forEach { chunk ->
        val batch = batch()
        chunk.forEach { it(batch) }
        batch.commit().logFailure(operation)
    }
}

internal fun logListenerError(what: String, error: Throwable) {
    val code = (error as? FirebaseFirestoreException)?.code?.name ?: error.javaClass.simpleName
    Log.w(TAG, "$what 리스너 오류: $code")
}
