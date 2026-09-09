package com.github.chsssssss.eonje.data.local

import androidx.room.Entity

@Entity(primaryKeys = ["postId", "placeId"])
data class PostPlaceCrossRef(
    val postId: String,
    val placeId: String,
)
