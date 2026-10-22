package com.github.chsssssss.eonje.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.github.chsssssss.eonje.domain.repository.OnboardingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore는 Glance가 이미 끌어오는 의존성(datastore-preferences)을 그대로 쓴다.
 * 같은 파일에 DataStore 인스턴스가 둘 생기면 안 되므로 @Singleton으로 하나만 둔다.
 */
@Singleton
class OnboardingRepositoryImpl @Inject constructor(
    @ApplicationContext context: Context,
) : OnboardingRepository {

    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(DATASTORE_NAME) },
    )

    // 파일을 못 읽으면 온보딩을 한 번 더 보여주는 쪽이 앱이 멈추는 것보다 낫다.
    override val isCompleted: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[KEY_COMPLETED] ?: false }

    override suspend fun markCompleted() {
        dataStore.edit { it[KEY_COMPLETED] = true }
    }

    override suspend fun consumeFirstShareAfterOnboarding(): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            if (prefs[KEY_COMPLETED] == true && prefs[KEY_FIRST_SHARE_LOGGED] != true) {
                prefs[KEY_FIRST_SHARE_LOGGED] = true
                consumed = true
            }
        }
        return consumed
    }

    private companion object {
        const val DATASTORE_NAME = "onboarding"
        val KEY_COMPLETED = booleanPreferencesKey("completed")
        val KEY_FIRST_SHARE_LOGGED = booleanPreferencesKey("first_share_logged")
    }
}
