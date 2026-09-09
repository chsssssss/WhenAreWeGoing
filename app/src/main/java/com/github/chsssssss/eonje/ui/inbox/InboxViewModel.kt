package com.github.chsssssss.eonje.ui.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
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
                // UNRESOLVED는 캐시 미스(미등록 계정 포함)나 파싱 실패를 모두 포함한다.
                // 어느 쪽이든 계정을 등록하면 다음 동기화에서 자동 재매칭을 시도한다.
                showUnregisteredAccountBanner = posts.any { it.status == ResolveStatus.UNRESOLVED },
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
        extractedCount = extractedCount,
        thumbnailUrl = thumbnailUrl,
    )
}
