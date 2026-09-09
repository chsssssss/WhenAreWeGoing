package com.github.chsssssss.eonje.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.TokenStatusStore
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    tokenStatusStore: TokenStatusStore,
    watchedAccountRepository: WatchedAccountRepository,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        tokenStatusStore.instagramTokenExpired,
        watchedAccountRepository.observeAll(),
    ) { expired, accounts ->
        val lastSyncedAt = accounts.mapNotNull { it.lastSyncedAt }.maxOrNull()
        SettingsUiState(
            isTokenExpired = expired,
            tokenStatusLabel = if (expired) "만료됨 · 재연결이 필요해요" else "정상",
            lastSyncedLabel = lastSyncedAt?.let { "${RelativeTimeFormatter.format(it)} 동기화" },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )
}
