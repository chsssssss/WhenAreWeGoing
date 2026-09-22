package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.ThemeMode
import kotlinx.coroutines.flow.StateFlow

interface ThemePreferenceRepository {
    val themeMode: StateFlow<ThemeMode>
    fun setThemeMode(mode: ThemeMode)
}
