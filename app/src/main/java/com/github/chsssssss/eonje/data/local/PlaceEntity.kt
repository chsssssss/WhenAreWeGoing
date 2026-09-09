package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.chsssssss.eonje.domain.model.ResolveStatus

@Entity(tableName = "places", indices = [Index(value = ["kakaoPlaceId"])])
data class PlaceEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val kakaoPlaceId: String?,
    val category: String?,
    val memo: String?,
    val status: ResolveStatus,
    val createdAt: Long,
    val resolvedAt: Long?,
)
