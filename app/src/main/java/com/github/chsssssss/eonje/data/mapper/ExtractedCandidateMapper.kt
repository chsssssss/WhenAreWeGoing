package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.PlaceCandidate

fun ExtractedCandidateEntity.toDomain() = ExtractedCandidate(
    postId = postId,
    extractionIndex = extractionIndex,
    extractedName = extractedName,
    rank = rank,
    place = PlaceCandidate(
        kakaoPlaceId = kakaoPlaceId,
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
    ),
)

fun ExtractedCandidate.toEntity() = ExtractedCandidateEntity(
    postId = postId,
    extractionIndex = extractionIndex,
    extractedName = extractedName,
    rank = rank,
    kakaoPlaceId = place.kakaoPlaceId,
    name = place.name,
    address = place.address,
    category = place.category,
    latitude = place.latitude,
    longitude = place.longitude,
)
