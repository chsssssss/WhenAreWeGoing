package com.github.chsssssss.eonje.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.repository.OnboardingRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val _openHomeRequests = Channel<Unit>(Channel.CONFLATED)

    /** 위젯 탭으로 앱이 열렸을 때 홈 탭으로 돌아가라는 신호. */
    val openHomeRequests: Flow<Unit> = _openHomeRequests.receiveAsFlow()

    private val _widgetPlaceRequest = MutableStateFlow<String?>(null)

    /** 홈 시트에서 상세로 열어야 할 장소 id — 존재하고 확정된 장소일 때만 값이 들어온다. */
    val widgetPlaceRequest: StateFlow<String?> = _widgetPlaceRequest.asStateFlow()

    /**
     * 위젯에서 열림. 장소가 그 사이 삭제됐거나 확정 상태가 아니면 상세는 열지 않고 홈만 보여준다(크래시 없이).
     * 온보딩을 아직 안 끝냈으면 온보딩 흐름을 건너뛰지 않도록 아무것도 하지 않는다.
     */
    fun onOpenedFromWidget(placeId: String?) {
        viewModelScope.launch {
            if (onboardingCompleted.filterNotNull().first().not()) return@launch
            _openHomeRequests.trySend(Unit)
            if (placeId == null) return@launch
            val place = runCatching { placeRepository.findById(placeId) }.getOrNull()
            if (place?.status == ResolveStatus.RESOLVED) _widgetPlaceRequest.value = placeId
        }
    }

    fun onWidgetPlaceRequestHandled() {
        _widgetPlaceRequest.value = null
    }

    init {
        viewModelScope.launch {
            val completed = onboardingCompleted.filterNotNull().first()
            // 장소 목록을 못 읽어도 스플래시만 내리고, 오류 처리는 홈 화면의 원래 흐름에 맡긴다.
            if (completed) runCatching { placeRepository.observeAll().first() }
            _isReady.value = true
        }
    }
}
