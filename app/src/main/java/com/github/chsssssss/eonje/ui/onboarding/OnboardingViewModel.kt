package com.github.chsssssss.eonje.ui.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.domain.repository.OnboardingRepository
import com.github.chsssssss.eonje.ui.navigation.EonjeDestinations
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val onboardingRepository: OnboardingRepository,
    private val analytics: FirebaseAnalytics,
) : ViewModel() {

    private val entry: String =
        savedStateHandle[EonjeDestinations.ONBOARDING_ENTRY_ARG] ?: Events.OnboardingEntry.FIRST_RUN

    private val _finished = Channel<Unit>(Channel.BUFFERED)
    val finished = _finished.receiveAsFlow()

    init {
        // 화면 회전 등으로 ViewModel이 유지되는 동안엔 다시 찍히지 않는다.
        analytics.logEvent(Events.ONBOARDING_VIEWED) { param(Events.Param.SOURCE, entry) }
    }

    /** [page]는 0부터 — 이벤트에는 사람이 읽기 쉽게 1부터 남긴다. */
    fun onSkip(page: Int) {
        analytics.logEvent(Events.ONBOARDING_SKIPPED) {
            param(Events.Param.SOURCE, entry)
            param(Events.Param.STEP, (page + 1).toLong())
        }
        finish()
    }

    fun onComplete() {
        analytics.logEvent(Events.ONBOARDING_COMPLETED) {
            param(Events.Param.SOURCE, entry)
            param(Events.Param.STEP, OnboardingPages.size.toLong())
        }
        finish()
    }

    // 완료 여부를 저장한 다음에 화면을 닫아야 다음 실행에서 다시 뜨지 않는다.
    private fun finish() {
        viewModelScope.launch {
            onboardingRepository.markCompleted()
            _finished.send(Unit)
        }
    }
}
