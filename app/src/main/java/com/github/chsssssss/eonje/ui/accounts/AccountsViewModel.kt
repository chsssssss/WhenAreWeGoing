package com.github.chsssssss.eonje.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.local.WatchedAccountEntity
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
import com.github.chsssssss.eonje.domain.usecase.RegisterAccountResult
import com.github.chsssssss.eonje.domain.usecase.RegisterAccountUseCase
import com.github.chsssssss.eonje.domain.util.RelativeTimeFormatter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private data class LocalAddState(
    val isAddSheetVisible: Boolean = false,
    val usernameInput: String = "",
    val isRegistering: Boolean = false,
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val watchedAccountRepository: WatchedAccountRepository,
    private val registerAccountUseCase: RegisterAccountUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(LocalAddState())
    private val pendingDeleteUsername = MutableStateFlow<String?>(null)

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages = _toastMessages.receiveAsFlow()

    val uiState: StateFlow<AccountsUiState> = combine(
        watchedAccountRepository.observeAll(),
        local,
        pendingDeleteUsername,
    ) { accounts, localState, deleteUsername ->
        AccountsUiState(
            accounts = accounts.map { it.toUiModel() },
            isAddSheetVisible = localState.isAddSheetVisible,
            usernameInput = localState.usernameInput,
            isRegistering = localState.isRegistering,
            pendingDeleteUsername = deleteUsername,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountsUiState(),
    )

    fun onAddAccountClick() {
        local.update { it.copy(isAddSheetVisible = true, usernameInput = "") }
    }

    fun onDismissAddSheet() {
        if (local.value.isRegistering) return
        local.update { it.copy(isAddSheetVisible = false) }
    }

    fun onUsernameChange(value: String) {
        local.update { it.copy(usernameInput = value) }
    }

    fun onConfirmRegister() {
        val username = local.value.usernameInput
        if (username.isBlank() || local.value.isRegistering) return

        local.update { it.copy(isRegistering = true) }
        viewModelScope.launch {
            when (val result = registerAccountUseCase(username)) {
                is RegisterAccountResult.Registered -> {
                    _toastMessages.trySend("계정이 등록됐어요")
                    local.update { LocalAddState() }
                }
                is RegisterAccountResult.Failed -> {
                    _toastMessages.trySend(result.message)
                    local.update { it.copy(isRegistering = false) }
                }
            }
        }
    }

    fun onRetrySync(username: String) {
        viewModelScope.launch {
            when (val result = registerAccountUseCase(username)) {
                is RegisterAccountResult.Registered -> _toastMessages.trySend("동기화를 다시 시도했어요")
                is RegisterAccountResult.Failed -> _toastMessages.trySend(result.message)
            }
        }
    }

    fun onDeleteRequested(username: String) {
        pendingDeleteUsername.update { username }
    }

    fun onCancelDelete() {
        pendingDeleteUsername.update { null }
    }

    fun onConfirmDelete() {
        val username = pendingDeleteUsername.value ?: return
        viewModelScope.launch {
            watchedAccountRepository.delete(username)
            pendingDeleteUsername.update { null }
            _toastMessages.trySend("계정을 삭제했어요")
        }
    }

    private fun WatchedAccountEntity.toUiModel(): AccountUiModel {
        val statusText = when {
            lastSyncError != null -> "동기화 실패 · 다시 시도"
            lastSyncedAt != null -> "${RelativeTimeFormatter.format(lastSyncedAt)} 동기화"
            else -> "동기화 대기 중"
        }
        return AccountUiModel(
            username = "@$username",
            rawUsername = username,
            statusText = statusText,
            isError = lastSyncError != null,
            profileImageUrl = profileImageUrl,
        )
    }
}
