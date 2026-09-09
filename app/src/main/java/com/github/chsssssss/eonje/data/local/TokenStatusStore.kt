package com.github.chsssssss.eonje.data.local

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

private const val PREFS_NAME = "token_status"
private const val KEY_IG_TOKEN_EXPIRED = "ig_token_expired"

/** 인스타그램 연동 토큰의 만료 여부를 앱 전역에서 공유한다 — 계정마다가 아니라 토큰 하나에 대한 상태다. */
@Singleton
class TokenStatusStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _instagramTokenExpired = MutableStateFlow(prefs.getBoolean(KEY_IG_TOKEN_EXPIRED, false))
    val instagramTokenExpired: StateFlow<Boolean> = _instagramTokenExpired

    fun markInstagramTokenExpired() {
        prefs.edit { putBoolean(KEY_IG_TOKEN_EXPIRED, true) }
        _instagramTokenExpired.value = true
    }

    fun clearInstagramTokenExpired() {
        if (!_instagramTokenExpired.value) return
        prefs.edit { putBoolean(KEY_IG_TOKEN_EXPIRED, false) }
        _instagramTokenExpired.value = false
    }
}
