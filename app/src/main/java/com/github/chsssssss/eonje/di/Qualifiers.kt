package com.github.chsssssss.eonje.di

import javax.inject.Qualifier

/** 기존 Room 저장소 구현. 3단계(Room → Firestore 1회 이전)에서 읽기 전용으로 주입받는다. 기본 바인딩은 Firestore다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class RoomStore

/** 프로세스 수명 동안 도는 코루틴 스코프(Firestore 리스너 공유, 익명 로그인 재시도 등). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** named FirebaseApp "wherewegoing"(Blaze). Firestore·Auth는 이 앱에만 붙인다 — default 앱은 Gemini 전용(Spark)이다. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WherewegoingApp
