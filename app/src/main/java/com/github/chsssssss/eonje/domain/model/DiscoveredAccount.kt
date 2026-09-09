package com.github.chsssssss.eonje.domain.model

data class DiscoveredAccount(
    val username: String,
    val igUserId: String,
    val profileImageUrl: String?,
    val media: List<DiscoveredMedia>,
)

data class DiscoveredMedia(
    val shortcode: String,
    val caption: String?,
    val permalink: String,
    val mediaType: String,
    val mediaUrls: List<String>,
    val timestamp: Long,
)
