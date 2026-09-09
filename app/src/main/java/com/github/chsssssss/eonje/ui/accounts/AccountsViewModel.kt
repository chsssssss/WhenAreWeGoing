package com.github.chsssssss.eonje.ui.accounts

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(AccountsUiState())
    val uiState: StateFlow<AccountsUiState> = _uiState.asStateFlow()

    private val _toastMessages = Channel<String>(Channel.BUFFERED)
    val toastMessages = _toastMessages.receiveAsFlow()

    fun onAddAccountClick() {
        _uiState.update { it.copy(isAddSheetVisible = true) }
    }

    fun onDismissAddSheet() {
        _uiState.update { it.copy(isAddSheetVisible = false) }
    }

    fun onUsernameChange(value: String) {
        _uiState.update { it.copy(usernameInput = value) }
    }

    fun onConfirmRegister() {
        _toastMessages.trySend("계정 등록은 3단계에서 지원돼요")
        _uiState.update { it.copy(isAddSheetVisible = false) }
    }

    fun onRetrySync(username: String) {
        _toastMessages.trySend("동기화 재시도는 3단계에서 지원돼요")
    }
}
