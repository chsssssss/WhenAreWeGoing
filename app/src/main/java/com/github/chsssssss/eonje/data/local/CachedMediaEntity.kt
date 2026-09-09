package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "cached_media", indices = [Index(value = ["username"])])
data class CachedMediaEntity(
    @PrimaryKey val shortcode: String,
    val username: String,
    val caption: String?,
    val permalink: String,
    val mediaType: String, // IMAGE, VIDEO, CAROUSEL_ALBUM
    val mediaUrls: List<String>, // Converters로 직렬화, 캐러셀은 순서대로
    val timestamp: Long,
    val cachedAt: Long,
)
