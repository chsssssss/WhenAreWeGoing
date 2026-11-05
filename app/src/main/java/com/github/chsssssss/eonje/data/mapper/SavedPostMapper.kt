package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.SavedPost

fun SavedPostEntity.toDomain() = SavedPost(
    id = id,
    shortcode = shortcode,
    instagramUrl = instagramUrl,
    caption = caption,
    thumbnailUrl = thumbnailUrl,
    status = status,
    extractedCount = extractedCount,
    createdAt = createdAt,
    unresolvedReason = unresolvedReason,
)

fun SavedPost.toEntity() = SavedPostEntity(
    id = id,
    shortcode = shortcode,
    instagramUrl = instagramUrl,
    caption = caption,
    thumbnailUrl = thumbnailUrl,
    status = status,
    extractedCount = extractedCount,
    createdAt = createdAt,
    unresolvedReason = unresolvedReason,
)
