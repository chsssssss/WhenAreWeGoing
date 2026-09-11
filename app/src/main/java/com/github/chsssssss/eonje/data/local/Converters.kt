package com.github.chsssssss.eonje.data.local

import androidx.room.TypeConverter
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromResolveStatus(status: ResolveStatus): String = status.name

    @TypeConverter
    fun toResolveStatus(value: String): ResolveStatus = ResolveStatus.valueOf(value)

    @TypeConverter
    fun fromUnresolvedReason(reason: UnresolvedReason?): String? = reason?.name

    @TypeConverter
    fun toUnresolvedReason(value: String?): UnresolvedReason? = value?.let { UnresolvedReason.valueOf(it) }

    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(value)
}
