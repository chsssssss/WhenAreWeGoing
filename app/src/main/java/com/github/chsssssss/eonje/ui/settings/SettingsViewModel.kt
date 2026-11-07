package com.github.chsssssss.eonje.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.domain.repository.AccountRepository
import com.github.chsssssss.eonje.domain.repository.DeleteResult
import com.github.chsssssss.eonje.domain.repository.DeleteStep
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationPhase
import com.github.chsssssss.eonje.domain.repository.LegacyMigrationStatus
import com.github.chsssssss.eonje.domain.repository.LinkResult
import com.github.chsssssss.eonje.domain.repository.ThemePreferenceRepository
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePreferenceRepository: ThemePreferenceRepository,
    private val accountRepository: AccountRepository,
    private val legacyMigrationStatus: LegacyMigrationStatus,
    private val analytics: FirebaseAnalytics,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** 토스트로 보여줄 문구(이벤트성이라 상태에 넣지 않는다). */
    val messages = _messages.receiveAsFlow()

    init {
        themePreferenceRepository.themeMode
            .onEach { mode -> _uiState.update { it.copy(themeMode = mode) } }
            .launchIn(viewModelScope)
        accountRepository.state
            .onEach { account -> _uiState.update { it.copy(account = account) } }
            .launchIn(viewModelScope)
        legacyMigrationStatus.observePhase()
            .onEach { phase -> _uiState.update { it.copy(legacyMigrationPending = phase != LegacyMigrationPhase.NOT_NEEDED) } }
            .launchIn(viewModelScope)
    }

    fun onThemeModeSelected(mode: ThemeMode) {
        themePreferenceRepository.setThemeMode(mode)
    }

    // ── 계정 연결 ──

    fun onLinkStarted() {
        _uiState.update { it.copy(busy = AccountBusy.LINKING) }
        analytics.logEvent(Events.ACCOUNT_LINK_STARTED, null)
    }

    fun onLinkTokenFailed(cancelled: Boolean) {
        _uiState.update { it.copy(busy = null) }
        analytics.logEvent(Events.ACCOUNT_LINK_FAILED) {
            param(Events.Param.ERROR_TYPE, if (cancelled) Events.AccountError.CANCELLED else Events.AccountError.UNKNOWN)
        }
        if (!cancelled) _messages.trySend("Google 로그인에 실패했어요. 잠시 뒤 다시 시도해주세요")
    }

    fun onLinkToken(idToken: String) {
        viewModelScope.launch {
            val result = accountRepository.linkWithGoogle(idToken)
            _uiState.update { it.copy(busy = null) }
            when (result) {
                LinkResult.Success -> {
                    analytics.logEvent(Events.ACCOUNT_LINK_SUCCEEDED, null)
                    _messages.trySend("데이터를 계정에 백업했어요")
                }
                LinkResult.Collision -> {
                    analytics.logEvent(Events.ACCOUNT_LINK_FAILED) { param(Events.Param.ERROR_TYPE, Events.AccountError.COLLISION) }
                    _messages.trySend("이미 다른 기기에서 쓰고 있는 Google 계정이에요")
                }
                LinkResult.NetworkError -> {
                    analytics.logEvent(Events.ACCOUNT_LINK_FAILED) { param(Events.Param.ERROR_TYPE, Events.AccountError.NETWORK) }
                    _messages.trySend("인터넷에 연결되어 있는지 확인해주세요")
                }
                LinkResult.Failed -> {
                    analytics.logEvent(Events.ACCOUNT_LINK_FAILED) { param(Events.Param.ERROR_TYPE, Events.AccountError.UNKNOWN) }
                    _messages.trySend("계정을 연결하지 못했어요. 잠시 뒤 다시 시도해주세요")
                }
            }
        }
    }

    // ── 다이얼로그 ──

    fun onShowDialog(dialog: AccountDialog) = _uiState.update { it.copy(dialog = dialog) }
    fun onDismissDialog() = _uiState.update { it.copy(dialog = null) }

    // ── 로그아웃 ──

    fun onSignOutConfirmed() {
        _uiState.update { it.copy(dialog = null, busy = AccountBusy.SIGNING_OUT) }
        viewModelScope.launch {
            accountRepository.signOut()
            analytics.logEvent(Events.ACCOUNT_SIGNED_OUT, null)
            _uiState.update { it.copy(busy = null) }
            _messages.trySend("로그아웃했어요")
        }
    }

    // ── 삭제 ──

    fun onDeleteStarted() {
        _uiState.update { it.copy(dialog = null, busy = AccountBusy.DELETING) }
    }

    /** 재인증 토큰을 못 받았을 때(취소·실패). 삭제는 시작하지 않는다. */
    fun onDeleteReauthFailed(cancelled: Boolean) {
        _uiState.update { it.copy(busy = null) }
        if (!cancelled) _messages.trySend("본인 확인에 실패했어요. 다시 시도해주세요")
    }

    /** [googleIdToken]은 연결된 계정의 재인증용(익명이면 null). */
    fun onDeleteConfirmed(googleIdToken: String?) {
        viewModelScope.launch {
            val result = accountRepository.deleteAccount(googleIdToken)
            _uiState.update { it.copy(busy = null) }
            when (result) {
                DeleteResult.Success -> {
                    analytics.logEvent(Events.ACCOUNT_DELETED, null)
                    _messages.trySend("삭제했어요")
                }
                is DeleteResult.Failed -> {
                    analytics.logEvent(Events.ACCOUNT_DELETE_FAILED) { param(Events.Param.STEP, result.step.name.lowercase()) }
                    _messages.trySend(
                        when (result.step) {
                            DeleteStep.REAUTH -> "본인 확인에 실패했어요. 다시 시도해주세요"
                            DeleteStep.DATA -> "데이터를 지우는 중에 멈췄어요. 인터넷을 확인하고 다시 시도해주세요"
                            DeleteStep.ACCOUNT ->
                                if (result.requiresReauth) "데이터는 지워졌어요. 계정을 지우려면 다시 로그인이 필요해요. 한 번 더 시도해주세요"
                                else "데이터는 지워졌어요. 계정을 지우는 중에 멈췄어요. 다시 시도해주세요"
                        },
                    )
                }
            }
        }
    }
}
