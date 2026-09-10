package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.DiscoveredAccount
import com.github.chsssssss.eonje.domain.model.DiscoveredMedia

interface InstagramBusinessDiscoveryRepository {
    /** username이 발견 가능한(공개 비즈니스/크리에이터) 계정인지 확인하고 최근 게시물을 함께 가져온다. */
    suspend fun discover(username: String): Result<DiscoveredAccount>

    /**
     * 평소 캐시(최근 25건)에 없는 오래된 게시물을 shortcode로 찾는다.
     * 계정의 게시물을 페이지 넘겨가며 뒤져보고, 못 찾으면 null을 반환한다(에러는 아님).
     */
    suspend fun findMediaByShortcode(username: String, shortcode: String): Result<DiscoveredMedia?>
}

/** Graph API가 OAuth 토큰 만료/무효(에러 코드 190)를 응답했을 때. 동기화를 중단하고 배너로 안내해야 한다. */
class InstagramTokenExpiredException(message: String) : Exception(message)
