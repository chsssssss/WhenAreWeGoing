package com.github.chsssssss.eonje.data.local

import androidx.room.TypeConverter
import com.github.chsssssss.eonje.domain.model.ResolveStatus

class Converters {
    @TypeConverter
    fun fromResolveStatus(status: ResolveStatus): String = status.name

    @TypeConverter
    fun toResolveStatus(value: String): ResolveStatus = ResolveStatus.valueOf(value)
}
