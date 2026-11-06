package com.github.chsssssss.eonje.data.migration

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 이전 순서대로: 폴더 → 장소 → 게시물. */
enum class MigrationKind { FOLDERS, PLACES, POSTS }

/**
 * Room → Firestore 이전의 진행 기록. **uid와 함께** 저장한다 — 자동 백업이 이 파일을 새 기기로 복원했는데 거기선 다른 uid로
 * 로그인되는 경우, 저장된 uid와 현재 uid가 달라서 처음부터 다시 이전하게 하려는 것이다.
 *
 * [cursor]는 종류별로 "여기까지 서버에 확정됐다"는 마지막 문서 id이고, [done]이면 그 종류는 끝난 것이다.
 */
data class MigrationState(
    val uid: String? = null,
    val completed: Boolean = false,
    val startedAtMillis: Long = 0L,
    val folderCursor: String = "",
    val placeCursor: String = "",
    val postCursor: String = "",
    val foldersDone: Boolean = false,
    val placesDone: Boolean = false,
    val postsDone: Boolean = false,
    val folderCount: Int = 0,
    val placeCount: Int = 0,
    val postCount: Int = 0,
) {
    fun cursor(kind: MigrationKind) = when (kind) {
        MigrationKind.FOLDERS -> folderCursor
        MigrationKind.PLACES -> placeCursor
        MigrationKind.POSTS -> postCursor
    }

    fun isDone(kind: MigrationKind) = when (kind) {
        MigrationKind.FOLDERS -> foldersDone
        MigrationKind.PLACES -> placesDone
        MigrationKind.POSTS -> postsDone
    }
}

/**
 * DataStore(별도 파일 `room_migration`)에 저장한다 — Room 스키마를 바꾸지 않으려고 DB가 아니라 이 파일을 쓴다.
 * 같은 파일에 DataStore 인스턴스가 둘 생기면 안 되므로 @Singleton이다.
 *
 * 모든 쓰기는 지금 저장된 uid가 호출한 uid와 같을 때만 반영한다(다른 uid의 진행 기록을 섞어 쓰지 않는다).
 */
@Singleton
class RoomMigrationStateStore(private val dataStore: DataStore<Preferences>) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) }),
    )

    // 파일을 못 읽으면 "기록 없음"으로 본다 → 처음부터 다시 이전하지만, 이미 서버에 있는 문서는 건너뛰므로 덮어쓰지 않는다.
    suspend fun read(): MigrationState = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .first()
        .toState()

    /** 진행 기록의 변화. 홈 화면이 이전 상태를 보여주는 데 쓴다(읽기만 하며 서버 호출이 아니다). */
    val states: Flow<MigrationState> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toState() }

    /** 이 uid로 새로 시작한다. 이전 uid의 진행 기록은 버린다. */
    suspend fun begin(uid: String, nowMillis: Long) {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[KEY_UID] = uid
            prefs[KEY_STARTED_AT] = nowMillis
        }
    }

    /** [cursor]까지 서버에 확정됐고 그중 [processed]개를 처리했다. 서버 확정 **뒤에** 부른다. */
    suspend fun saveProgress(uid: String, kind: MigrationKind, cursor: String, processed: Int) {
        dataStore.edit { prefs ->
            if (prefs[KEY_UID] != uid) return@edit
            when (kind) {
                MigrationKind.FOLDERS -> { prefs[KEY_FOLDER_CURSOR] = cursor; prefs[KEY_FOLDER_COUNT] = (prefs[KEY_FOLDER_COUNT] ?: 0) + processed }
                MigrationKind.PLACES -> { prefs[KEY_PLACE_CURSOR] = cursor; prefs[KEY_PLACE_COUNT] = (prefs[KEY_PLACE_COUNT] ?: 0) + processed }
                MigrationKind.POSTS -> { prefs[KEY_POST_CURSOR] = cursor; prefs[KEY_POST_COUNT] = (prefs[KEY_POST_COUNT] ?: 0) + processed }
            }
        }
    }

    suspend fun markKindDone(uid: String, kind: MigrationKind) {
        dataStore.edit { prefs ->
            if (prefs[KEY_UID] != uid) return@edit
            when (kind) {
                MigrationKind.FOLDERS -> prefs[KEY_FOLDERS_DONE] = true
                MigrationKind.PLACES -> prefs[KEY_PLACES_DONE] = true
                MigrationKind.POSTS -> prefs[KEY_POSTS_DONE] = true
            }
        }
    }

    /** 끝났다. 진행 기록(커서·개수)은 남겨둬서 완료 보고에 쓴다. */
    suspend fun markCompleted(uid: String) {
        dataStore.edit { prefs ->
            if (prefs[KEY_UID] != uid) {
                prefs.clear()
                prefs[KEY_UID] = uid
            }
            prefs[KEY_COMPLETED] = true
        }
    }

    private fun Preferences.toState() = MigrationState(
        uid = this[KEY_UID],
        completed = this[KEY_COMPLETED] ?: false,
        startedAtMillis = this[KEY_STARTED_AT] ?: 0L,
        folderCursor = this[KEY_FOLDER_CURSOR] ?: "",
        placeCursor = this[KEY_PLACE_CURSOR] ?: "",
        postCursor = this[KEY_POST_CURSOR] ?: "",
        foldersDone = this[KEY_FOLDERS_DONE] ?: false,
        placesDone = this[KEY_PLACES_DONE] ?: false,
        postsDone = this[KEY_POSTS_DONE] ?: false,
        folderCount = this[KEY_FOLDER_COUNT] ?: 0,
        placeCount = this[KEY_PLACE_COUNT] ?: 0,
        postCount = this[KEY_POST_COUNT] ?: 0,
    )

    private companion object {
        const val DATASTORE_NAME = "room_migration"
        val KEY_UID = stringPreferencesKey("migrated_for_uid")
        val KEY_COMPLETED = booleanPreferencesKey("completed")
        val KEY_STARTED_AT = longPreferencesKey("started_at")
        val KEY_FOLDER_CURSOR = stringPreferencesKey("folder_cursor")
        val KEY_PLACE_CURSOR = stringPreferencesKey("place_cursor")
        val KEY_POST_CURSOR = stringPreferencesKey("post_cursor")
        val KEY_FOLDERS_DONE = booleanPreferencesKey("folders_done")
        val KEY_PLACES_DONE = booleanPreferencesKey("places_done")
        val KEY_POSTS_DONE = booleanPreferencesKey("posts_done")
        val KEY_FOLDER_COUNT = intPreferencesKey("folder_count")
        val KEY_PLACE_COUNT = intPreferencesKey("place_count")
        val KEY_POST_COUNT = intPreferencesKey("post_count")
    }
}
