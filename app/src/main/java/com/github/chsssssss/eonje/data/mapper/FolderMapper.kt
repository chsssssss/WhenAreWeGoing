package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.domain.model.Folder

fun FolderEntity.toDomain() = Folder(
    id = id,
    name = name,
    createdAt = createdAt,
    color = color,
    iconKey = iconKey,
)

fun Folder.toEntity() = FolderEntity(
    id = id,
    name = name,
    createdAt = createdAt,
    color = color,
    iconKey = iconKey,
)
