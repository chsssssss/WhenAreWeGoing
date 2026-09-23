package com.github.chsssssss.eonje.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.domain.repository.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferenceRepository: ThemePreferenceRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        themePreferenceRepository.themeMode
            .onEach { mode -> _uiState.update { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        themePreferenceRepository.setThemeMode(mode)
    }
}
