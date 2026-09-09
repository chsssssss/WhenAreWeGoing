package com.github.chsssssss.eonje.ui.accounts

data class AccountUiModel(
    val username: String,
    val statusText: String,
    val isError: Boolean = false,
)

data class AccountsUiState(
    val accounts: List<AccountUiModel> = emptyList(),
    val isAddSheetVisible: Boolean = false,
    val usernameInput: String = "",
    val isRegistering: Boolean = false,
)
