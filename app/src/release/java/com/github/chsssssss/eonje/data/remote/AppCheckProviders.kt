package com.github.chsssssss.eonje.data.remote

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** 릴리스 빌드 — Play에서 설치한 앱만 통과한다(Play Integrity). */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
