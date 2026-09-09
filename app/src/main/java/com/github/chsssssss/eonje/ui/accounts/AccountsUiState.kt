package com.github.chsssssss.eonje.ui.accounts

data class AccountUiModel(
    val username: String,
    val statusText: String,
    val isError: Boolean = false,
)

data class AccountsUiState(
    val accounts: List<AccountUiModel> = defaultMockAccounts(),
    val isAddSheetVisible: Boolean = false,
    val usernameInput: String = "seongsu.list",
)

internal fun defaultMockAccounts() = listOf(
    AccountUiModel("@seongsu.list", "10분 전 동기화 · 새 게시물 3"),
    AccountUiModel("@seoul.bap", "2시간 전 동기화"),
    AccountUiModel("@mangwon.eats", "어제 오후 8:30 동기화"),
    AccountUiModel("@cafe.route", "동기화 실패 · 다시 시도", isError = true),
)
