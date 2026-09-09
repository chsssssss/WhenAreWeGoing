package com.github.chsssssss.eonje.domain.util

import java.time.Duration
import java.time.Instant

object RelativeTimeFormatter {
    fun format(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val seconds = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.ofEpochMilli(nowMillis)).seconds
        return when {
            seconds < 60 -> "방금 전"
            seconds < 3600 -> "${seconds / 60}분 전"
            seconds < 86400 -> "${seconds / 3600}시간 전"
            seconds < 86400 * 7 -> "${seconds / 86400}일 전"
            else -> "${seconds / (86400 * 7)}주 전"
        }
    }
}
