package com.github.chsssssss.eonje.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class InboxViewModel @Inject constructor(
    repository: SavedPostRepository
) : ViewModel() {

    val uiState: StateFlow<InboxUiState> = repository.observeUnresolved()
        .map { posts ->
            InboxUiState(
                items = posts.map { it.toInboxItem() },
                isLoading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InboxUiState(),
        )

    private fun SavedPostEntity.toInboxItem() = InboxItem(
        id = id,
        instagramUrl = instagramUrl,
        relativeTime = RelativeTimeFormatter.format(createdAt),
    )
}
