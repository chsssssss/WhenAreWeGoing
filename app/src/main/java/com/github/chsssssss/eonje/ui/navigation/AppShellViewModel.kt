package com.github.chsssssss.eonje.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Backs the bottom nav bar's inbox badge — owned at the app-shell level, not a single screen. */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    repository: SavedPostRepository
) : ViewModel() {
    val inboxBadgeCount: StateFlow<Int> = repository.observeUnresolved()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
}
