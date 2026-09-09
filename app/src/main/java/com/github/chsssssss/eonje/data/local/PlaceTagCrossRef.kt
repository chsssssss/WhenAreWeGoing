package com.github.chsssssss.eonje.data.local

import androidx.room.Entity

@Entity(primaryKeys = ["placeId", "tagId"])
data class PlaceTagCrossRef(
    val placeId: String,
    val tagId: String,
)
