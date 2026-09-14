package com.github.chsssssss.eonje.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.github.chsssssss.eonje.domain.model.ResolveStatus

@Entity(tableName = "places", indices = [Index(value = ["kakaoPlaceId"]), Index(value = ["folderId"])])
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
    // null이면 미분류. 폴더는 장소당 하나만 가질 수 있다(다대다 아님).
    val folderId: String? = null,
)
