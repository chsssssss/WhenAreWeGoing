package com.github.chsssssss.eonje.data.local

import androidx.room.TypeConverter
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromResolveStatus(status: ResolveStatus): String = status.name

    @TypeConverter
    fun toResolveStatus(value: String): ResolveStatus = ResolveStatus.valueOf(value)

    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}
