package com.github.chsssssss.eonje.ui.accounts

data class AccountUiModel(
    val username: String,
    val statusText: String,
    val isError: Boolean = false,
    val profileImageUrl: String? = null,
)

data class AccountsUiState(
    val accounts: List<AccountUiModel> = emptyList(),
    val isAddSheetVisible: Boolean = false,
    val usernameInput: String = "",
    val isRegistering: Boolean = false,
)
