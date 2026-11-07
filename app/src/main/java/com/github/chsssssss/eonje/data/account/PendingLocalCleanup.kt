package com.github.chsssssss.eonje.data.account

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 계정 삭제 뒤 아직 끝나지 않은 로컬 정리의 "표시". 서버 삭제가 확정되고 Room 이전이 영구 종료된 직후, 로컬 정리를 시작하기 **전에** 남기고
 * 정리가 모두 성공하면 지운다. 정리가 실패하거나 앱이 죽어서 중단되면 남아서, 다음 앱 시작에 이어서 정리한다.
 *
 * - [thumbnailKeys]: 그 시점에 기기에 있던 썸네일의 postId 목록. **이 이름만** 지운다 — 재시도 시점에 새 익명 계정이 저장한 게시물의
 *   썸네일은 이 목록에 없다.
 * - [markedAtMillis]: 표시를 남긴 시각. 게시물 ID는 공유 키에서 만든 결정적 UUID라서, 새 계정이 같은 인스타 게시물을 다시 저장하면 **같은 이름**의
 *   파일이 새로 만들어질 수 있다. 이 시각 이후에 바뀐 파일은 새 계정의 것으로 보고 지우지 않는다.
 * - [uid]: 삭제한 계정. 장소 동기화 기록을 이 uid 것만 지운다(새 계정의 기록은 건드리지 않는다).
 */
data class PendingCleanup(
    val uid: String,
    val thumbnailKeys: Set<String>,
    val markedAtMillis: Long,
)

/**
 * DataStore(별도 파일 `account_cleanup`)에 저장한다. **자동 백업에서 제외**한다(`backup_rules.xml`, `data_extraction_rules.xml`) — 다른 기기에
 * 복원돼서 그 기기엔 없는 파일을 지우려 들 이유가 없다. 같은 파일에 DataStore 인스턴스가 둘 생기면 안 되므로 @Singleton이다.
 */
@Singleton
class PendingLocalCleanupStore(private val dataStore: DataStore<Preferences>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }),
    )

    /** 읽지 못하면 "표시 없음"으로 본다(정리를 영영 못 하게 막는 것보다, 한 번 건너뛰는 쪽이 낫다). */
    suspend fun read(): PendingCleanup? {
        val prefs = dataStore.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.first()
        if (prefs[KEY_PENDING] != true) return null
        return PendingCleanup(
            uid = prefs[KEY_UID].orEmpty(),
            thumbnailKeys = prefs[KEY_THUMBNAIL_KEYS].orEmpty(),
            markedAtMillis = prefs[KEY_MARKED_AT] ?: 0L,
        )
    }

    /**
     * 표시를 남긴다. 이미 표시가 있으면(계정 삭제를 다시 시도한 경우 등) 이름 목록을 합치고 시각은 더 나중 것을 쓴다 — 나중에 만든 표시는 그 사이에
     * 저장된 게시물까지 담고, 새 계정은 마지막 표시 이후에야 생기기 때문이다.
     */
    suspend fun markPending(uid: String, thumbnailKeys: Set<String>, nowMillis: Long): PendingCleanup {
        var result = PendingCleanup(uid, thumbnailKeys, nowMillis)
        dataStore.edit { prefs ->
            val merged = PendingCleanup(
                uid = uid,
                thumbnailKeys = (prefs[KEY_THUMBNAIL_KEYS].orEmpty().takeIf { prefs[KEY_PENDING] == true }.orEmpty()) + thumbnailKeys,
                markedAtMillis = maxOf(nowMillis, prefs[KEY_MARKED_AT] ?: 0L),
            )
            prefs[KEY_PENDING] = true
            prefs[KEY_UID] = merged.uid
            prefs[KEY_THUMBNAIL_KEYS] = merged.thumbnailKeys
            prefs[KEY_MARKED_AT] = merged.markedAtMillis
            result = merged
        }
        return result
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(KEY_PENDING)
            prefs.remove(KEY_UID)
            prefs.remove(KEY_THUMBNAIL_KEYS)
            prefs.remove(KEY_MARKED_AT)
        }
    }

    companion object {
        const val DATASTORE_NAME = "account_cleanup"
        private val KEY_PENDING = booleanPreferencesKey("pending")
        private val KEY_UID = stringPreferencesKey("uid")
        private val KEY_THUMBNAIL_KEYS = stringSetPreferencesKey("thumbnail_keys")
        private val KEY_MARKED_AT = longPreferencesKey("marked_at")
    }
}

/** 시작 때 하는 재시도의 결과를 알린다(결과만, 값 없이). 테스트에선 기록용으로 바꾼다. */
fun interface CleanupRetryEvents {
    fun retried(success: Boolean)
}

@Singleton
class FirebaseCleanupRetryEvents @Inject constructor(private val analytics: FirebaseAnalytics) : CleanupRetryEvents {
    override fun retried(success: Boolean) {
        analytics.logEvent(Events.ACCOUNT_LOCAL_CLEANUP_RETRY) {
            param(Events.Param.STATUS, if (success) Events.Status.SUCCESS else Events.Status.ERROR)
        }
    }
}

/**
 * 계정 삭제 뒤 로컬 정리를 "표시 → 정리 → 표시 지움" 순서로 하고, 끝나지 않았으면 다음 시작에 이어서 한다.
 *
 * 정리는 **멱등**이다(이미 지워진 상태에서 다시 해도 안전하다). 한 단계가 실패해도 나머지 단계는 계속하고, 하나라도 실패하면 표시를 남긴다.
 * 재시도가 반복해서 실패해도 사용자에게 오류를 보여주지 않는다 — 로그와 Analytics 이벤트(결과만)만 남긴다.
 *
 * 썸네일은 표시에 담긴 이름만 지우고, 새 계정의 것으로 보이는 파일은 지우지 않는다:
 * - 표시를 남긴 시각 이후에 바뀐 파일(새 계정이 같은 게시물을 다시 저장해 새로 받은 파일)
 * - 지금 로그인된 계정이 아는 게시물의 파일(이미 있던 파일을 그대로 쓰는 경우)
 */
class PendingLocalCleanup(
    private val store: PendingLocalCleanupStore,
    private val cleanup: AccountLocalCleanup,
    private val protectedThumbnailKeys: suspend () -> Set<String>,
    private val events: CleanupRetryEvents,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        store: PendingLocalCleanupStore,
        cleanup: AccountLocalCleanup,
        posts: SavedPostRepository,
        events: CleanupRetryEvents,
    ) : this(store, cleanup, { posts.cachedPostIds() }, events)

    /**
     * 로컬 정리를 시작하기 **전에** 표시를 남긴다. 표시를 못 남겨도(저장소 오류) 지금 하는 정리는 계속할 수 있게 표시 내용은 돌려준다.
     * 이 시점에 기기에 있는 썸네일 이름을 함께 담는다.
     */
    suspend fun begin(uid: String): PendingCleanup {
        val keys = try {
            cleanup.listThumbnailKeys()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "썸네일 목록 읽기 실패(${e.javaClass.simpleName})")
            emptySet()
        }
        val now = nowMillis()
        return try {
            store.markPending(uid, keys, now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "정리 대기 표시 저장 실패(${e.javaClass.simpleName})")
            PendingCleanup(uid, keys, now)
        }
    }

    /** [pending]을 정리하고, 모두 성공하면 표시를 지운다. 성공 여부를 돌려준다. */
    suspend fun finish(pending: PendingCleanup): Boolean {
        val ok = execute(pending)
        if (ok) {
            try {
                store.clear()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "정리 대기 표시 삭제 실패(${e.javaClass.simpleName}) — 다음 시작에 다시 확인한다")
            }
        }
        return ok
    }

    /** 앱 시작 때: 표시가 있으면 정리를 다시 실행한다. 표시가 없으면 아무것도 지우지 않는다. 실패해도 예외·화면 오류 없이 다음 시작으로 미룬다. */
    suspend fun retryIfPending() {
        val pending = try {
            store.read()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "정리 대기 표시 읽기 실패(${e.javaClass.simpleName})")
            null
        } ?: return
        val ok = finish(pending)
        Log.i(TAG, "로컬 정리 재시도: ${if (ok) "성공" else "실패 — 다음 시작에 다시 시도"}")
        events.retried(ok)
    }

    private suspend fun execute(pending: PendingCleanup): Boolean {
        var ok = true
        suspend fun step(what: String, block: suspend () -> Unit) {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ok = false
                Log.w(TAG, "$what 실패(${e.javaClass.simpleName})")
            }
        }
        step("썸네일 정리") { cleanup.clearThumbnails(pending.thumbnailKeys, pending.markedAtMillis, protectedThumbnailKeys()) }
        step("동기화 기록 정리") { cleanup.clearPlaceSyncFor(pending.uid) }
        step("Room 비우기") { cleanup.clearLegacyRoom() }
        step("캐시 정리 예약") { cleanup.requestFirestoreCacheClear() }
        return ok
    }

    private companion object {
        const val TAG = "EonjeAccount"
    }
}
