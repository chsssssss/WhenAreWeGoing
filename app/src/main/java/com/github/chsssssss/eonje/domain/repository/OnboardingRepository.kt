package com.github.chsssssss.eonje.domain.repository

import kotlinx.coroutines.flow.Flow

interface OnboardingRepository {
    /** 첫 실행 온보딩을 끝냈는지(시작하기·건너뛰기 둘 다 포함). */
    val isCompleted: Flow<Boolean>

    suspend fun markCompleted()

    /** 온보딩을 끝낸 뒤의 첫 공유 저장이면 true — 한 번 true를 돌려준 뒤로는 계속 false. */
    suspend fun consumeFirstShareAfterOnboarding(): Boolean
}
