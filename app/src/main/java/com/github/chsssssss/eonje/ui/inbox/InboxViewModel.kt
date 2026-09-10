package com.github.chsssssss.eonje.ui.inbox

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import com.github.chsssssss.eonje.widget.InboxWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private val STALE_THRESHOLD_MILLIS = TimeUnit.DAYS.toMillis(30)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: SavedPostRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val showCleanupDialog = MutableStateFlow(false)
    private val pendingDeleteId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<InboxUiState> = combine(
        repository.observeUnresolved(),
        showCleanupDialog,
        pendingDeleteId,
    ) { posts, showDialog, deleteId ->
        val staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MILLIS
        InboxUiState(
            items = posts.map { it.toInboxItem() },
            isLoading = false,
            // UNRESOLVED는 캐시 미스(미등록 계정 포함)나 파싱 실패를 모두 포함한다.
            // 어느 쪽이든 계정을 등록하면 다음 동기화에서 자동 재매칭을 시도한다.
            showUnregisteredAccountBanner = posts.any { it.status == ResolveStatus.UNRESOLVED },
            staleItemIds = posts.filter { it.createdAt < staleThreshold }.map { it.id },
            showCleanupDialog = showDialog,
            pendingDeleteId = deleteId,
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = InboxUiState(),
        )

    fun onCleanupStaleClick() {
        showCleanupDialog.update { true }
    }

    fun onDismissCleanupDialog() {
        showCleanupDialog.update { false }
    }

    fun onConfirmCleanup() {
        val staleIds = uiState.value.staleItemIds
        if (staleIds.isEmpty()) return
        viewModelScope.launch {
            repository.deleteByIds(staleIds)
            showCleanupDialog.update { false }
            InboxWidget().updateAll(context)
        }
    }

    fun onDeleteRequested(postId: String) {
        pendingDeleteId.update { postId }
    }

    fun onCancelDelete() {
        pendingDeleteId.update { null }
    }

    fun onConfirmDelete() {
        val postId = pendingDeleteId.value ?: return
        viewModelScope.launch {
            repository.deleteByIds(listOf(postId))
            pendingDeleteId.update { null }
            InboxWidget().updateAll(context)
        }
    }

    private fun SavedPostEntity.toInboxItem() = InboxItem(
        id = id,
        instagramUrl = instagramUrl,
        relativeTime = RelativeTimeFormatter.format(createdAt),
        extractedCount = extractedCount,
        thumbnailUrl = thumbnailUrl,
    )
}
