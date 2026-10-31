package com.github.chsssssss.eonje.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.domain.repository.OnboardingRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the bottom nav bar's inbox badge — owned at the app-shell level, not a single screen. */
@HiltViewModel
class AppShellViewModel @Inject constructor(
    repository: SavedPostRepository,
    onboardingRepository: OnboardingRepository,
    private val placeRepository: PlaceRepository,
) : ViewModel() {
    val inboxBadgeCount: StateFlow<Int> = repository.observeUnresolved()
        .map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** null이면 아직 DataStore를 읽는 중 — 시작 화면(홈/온보딩)을 정하기 전까지 아무것도 그리지 않는다. */
    val onboardingCompleted: StateFlow<Boolean?> = onboardingRepository.isCompleted
        .stateIn<Boolean?>(viewModelScope, SharingStarted.Eagerly, null)

    private val _isReady = MutableStateFlow(false)

    /**
     * 스플래시를 내려도 되는지 — 시작 화면(홈/온보딩)이 정해졌고, 홈으로 간다면 저장된 장소 목록을
     * Room에서 한 번 읽어온 상태. 첫 읽기에서 DB 파일을 여는 비용을 스플래시 뒤에서 치러두면 홈이 빈
     * 화면으로 깜빡이지 않는다. MainActivity가 최대 유지 시간 상한을 따로 두므로 여기서 실패해도 무한 대기는 없다.
     */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    init {
        viewModelScope.launch {
            val completed = onboardingCompleted.filterNotNull().first()
            // 장소 목록을 못 읽어도 스플래시만 내리고, 오류 처리는 홈 화면의 원래 흐름에 맡긴다.
            if (completed) runCatching { placeRepository.observeAll().first() }
            _isReady.value = true
        }
    }
}
