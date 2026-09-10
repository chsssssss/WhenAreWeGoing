package com.github.chsssssss.eonje.data.remote.instagram

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BusinessDiscoveryResponse(
    @SerialName("business_discovery") val businessDiscovery: BusinessDiscoveryDto? = null,
    val error: GraphApiErrorDto? = null,
)

@Serializable
data class BusinessDiscoveryDto(
    val id: String? = null,
    val username: String? = null,
    @SerialName("profile_picture_url") val profilePictureUrl: String? = null,
    val media: MediaConnectionDto? = null,
)

@Serializable
data class MediaConnectionDto(
    val data: List<MediaItemDto> = emptyList(),
    val paging: PagingDto? = null,
)

@Serializable
data class PagingDto(
    val cursors: CursorsDto? = null,
)

@Serializable
data class CursorsDto(
    val after: String? = null,
)

@Serializable
data class MediaItemDto(
    val id: String,
    val permalink: String,
    val caption: String? = null,
    val timestamp: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String,
    val children: ChildrenConnectionDto? = null,
)

@Serializable
data class ChildrenConnectionDto(
    val data: List<ChildMediaDto> = emptyList(),
)

@Serializable
data class ChildMediaDto(
    val id: String,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
)

@Serializable
data class GraphApiErrorDto(
    val message: String? = null,
    val type: String? = null,
    val code: Int? = null,
)
