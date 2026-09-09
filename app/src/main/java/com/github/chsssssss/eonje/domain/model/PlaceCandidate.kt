package com.github.chsssssss.eonje.domain.model

data class PlaceCandidate(
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
)
