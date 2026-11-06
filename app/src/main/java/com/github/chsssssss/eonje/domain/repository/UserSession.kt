package com.github.chsssssss.eonje.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 저장소를 쓰려면 필요한 익명 로그인 세션. 설치 직후 오프라인이면 아직 로그인 전일 수 있다. */
interface UserSession {
    /** 기다리지 않고 지금 로그인돼 있는지만 본다(메인 스레드에서 불러도 안전). */
    fun isSignedIn(): Boolean

    /** 지금 로그인된 uid의 흐름. 로그인 전이면 null. */
    val uid: StateFlow<String?>

    /** 지금 로그인된 uid. 기다리지 않는다. 로그인 전이면 null. */
    fun currentUid(): String?

    /** 로그인 전이면 익명 로그인을 시도한다. 로그인된 상태면 true, 실패(오프라인 등)면 false. 동시에 불러도 로그인은 한 번만 일어난다. */
    suspend fun signInIfNeeded(): Boolean
}
