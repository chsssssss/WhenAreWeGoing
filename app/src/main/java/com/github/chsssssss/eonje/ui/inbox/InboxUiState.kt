package com.github.chsssssss.eonje.ui.inbox

data class InboxUiState(
    val items: List<InboxItem> = emptyList(),
    val isLoading: Boolean = true,
)

data class InboxItem(
    val id: String,
    val instagramUrl: String,
    val relativeTime: String,
    val extractedCount: Int = 0,
)
