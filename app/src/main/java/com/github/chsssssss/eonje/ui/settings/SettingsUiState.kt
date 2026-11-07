package com.github.chsssssss.eonje.ui.settings

import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.domain.model.ThemeMode
import com.github.chsssssss.eonje.domain.repository.AccountState
import com.github.chsssssss.eonje.domain.repository.MergePreview

/** 설정 화면에서 지금 진행 중인 계정 작업. 진행 중엔 버튼을 막는다. */
enum class AccountBusy { LINKING, SIGNING_OUT, DELETING }

/** 확인 다이얼로그. */
enum class AccountDialog { SIGN_OUT, DELETE_ACCOUNT, DELETE_DATA, MERGE }

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val account: AccountState = AccountState(),
    /** 웹 클라이언트 ID가 없으면 계정 섹션을 숨긴다(앱은 정상 동작). */
    val accountSectionVisible: Boolean = BuildConfig.GOOGLE_WEB_CLIENT_ID.isNotBlank(),
    /** Room 이전이 끝나기 전엔 계정 연결을 막는다 — 일부만 옮겨진 데이터가 계정과 섞이면 나머지가 영영 안 옮겨진다. */
    val legacyMigrationPending: Boolean = false,
    val busy: AccountBusy? = null,
    val dialog: AccountDialog? = null,
    /** 이미 쓰고 있던 계정으로 로그인할 때 "합칠까요?"에 보여줄, 이 기기에 있는 데이터 개수. */
    val mergePreview: MergePreview? = null,
)
