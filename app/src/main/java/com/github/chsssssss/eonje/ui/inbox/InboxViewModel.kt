package com.github.chsssssss.eonje.ui.inbox

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.data.worker.CaptionParsingWorker
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import com.github.chsssssss.eonje.widget.InboxWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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

    // 게시물별 CaptionParsingWorker가 아직 안 끝났으면 그 postId가 여기 들어있다 — 인박스에서 "정리 중" 표시용.
    private val processingPostIds = WorkManager.getInstance(context)
        .getWorkInfosByTagFlow(CaptionParsingWorker.TAG)
        .map { infos -> infos.filter { !it.state.isFinished }.flatMap(WorkInfo::tags).toSet() }

    val uiState: StateFlow<InboxUiState> = combine(
        repository.observeUnresolved(),
        showCleanupDialog,
        pendingDeleteId,
        processingPostIds,
    ) { posts, showDialog, deleteId, processingIds ->
        val staleThreshold = System.currentTimeMillis() - STALE_THRESHOLD_MILLIS
        InboxUiState(
            items = posts.map { it.toInboxItem(isProcessing = it.id in processingIds) },
            isLoading = false,
            showUnregisteredAccountBanner = posts.any {
                it.status == ResolveStatus.UNRESOLVED && it.unresolvedReason == UnresolvedReason.ACCOUNT_NOT_FOUND
            },
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

    private fun SavedPostEntity.toInboxItem(isProcessing: Boolean) = InboxItem(
        id = id,
        instagramUrl = instagramUrl,
        relativeTime = RelativeTimeFormatter.format(createdAt),
        extractedCount = extractedCount,
        thumbnailUrl = thumbnailUrl,
        isProcessing = isProcessing,
        unresolvedReason = unresolvedReason,
    )
}
