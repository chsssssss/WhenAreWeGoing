package com.github.chsssssss.eonje.data.remote.kakao

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KakaoLocalSearchResponse(
    @SerialName("documents") val documents: List<KakaoPlaceDocument> = emptyList(),
)

@Serializable
data class KakaoPlaceDocument(
    @SerialName("id") val id: String,
    @SerialName("place_name") val placeName: String,
    @SerialName("address_name") val addressName: String,
    @SerialName("road_address_name") val roadAddressName: String? = null,
    @SerialName("category_group_name") val categoryGroupName: String? = null,
    @SerialName("category_name") val categoryName: String? = null,
    @SerialName("x") val x: String,
    @SerialName("y") val y: String,
)
