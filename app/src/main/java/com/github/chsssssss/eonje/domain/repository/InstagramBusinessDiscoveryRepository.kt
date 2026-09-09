package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.DiscoveredAccount

interface InstagramBusinessDiscoveryRepository {
    /** username이 발견 가능한(공개 비즈니스/크리에이터) 계정인지 확인하고 최근 게시물을 함께 가져온다. */
    suspend fun discover(username: String): Result<DiscoveredAccount>
}
