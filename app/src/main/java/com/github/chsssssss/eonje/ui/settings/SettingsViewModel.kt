package com.github.chsssssss.eonje.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.debug.DaeguDummyDataSeeder
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.domain.repository.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val dummyDataSeeder: DaeguDummyDataSeeder,
    private val themePreferenceRepository: ThemePreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages = _toastMessages.receiveAsFlow()

    init {
        themePreferenceRepository.themeMode
            .onEach { mode -> _uiState.update { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        themePreferenceRepository.setThemeMode(mode)
    }

    /** 디버그 빌드에서만 SettingsScreen에 노출되는 스크린샷용 더미 데이터 채우기 — 기존 데이터를 전부 지운다. */
    fun onSeedDummyData() {
        if (_uiState.value.isSeeding) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSeeding = true) }
            dummyDataSeeder.seed()
            _uiState.update { it.copy(isSeeding = false) }
            _toastMessages.trySend("대구 더미 데이터로 채웠어요")
        }
    }
}
