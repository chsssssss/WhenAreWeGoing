package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.github.chsssssss.eonje.domain.model.FolderVisuals

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
    val color: Int = FolderVisuals.DEFAULT_COLOR,
    val iconKey: String = FolderVisuals.DEFAULT_ICON,
)
