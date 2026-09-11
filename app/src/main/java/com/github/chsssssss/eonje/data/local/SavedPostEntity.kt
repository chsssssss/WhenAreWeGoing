package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason

@Entity(tableName = "saved_posts", indices = [Index(value = ["shortcode"])])
data class SavedPostEntity(
    @PrimaryKey val id: String,
    val shortcode: String?,
    val instagramUrl: String,
    val caption: String?,
    val thumbnailUrl: String?,
    val status: ResolveStatus,
    val extractedCount: Int,
    val createdAt: Long,
    val unresolvedReason: UnresolvedReason? = null,
)
