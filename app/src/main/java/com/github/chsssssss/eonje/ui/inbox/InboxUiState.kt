package com.github.chsssssss.eonje.ui.inbox

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = true,
    val showUnregisteredAccountBanner: Boolean = false,
    val staleItemIds: List<String> = emptyList(),
    val showCleanupDialog: Boolean = false,
)

data class InboxItem(
    val id: String,
    val instagramUrl: String,
    val relativeTime: String,
    val extractedCount: Int = 0,
    val thumbnailUrl: String? = null,
)
