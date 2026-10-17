package com.github.chsssssss.eonje.data.remote

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * 디버그 빌드 전용 — 기기별 디버그 토큰을 각 Firebase 프로젝트 콘솔에 등록해야 통과한다.
 * firebase-appcheck-debug는 debugImplementation이라 릴리스엔 이 클래스와 SDK가 아예 들어가지 않는다.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = DebugAppCheckProviderFactory.getInstance()
