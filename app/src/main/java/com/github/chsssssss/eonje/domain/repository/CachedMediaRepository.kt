package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.data.local.CachedMediaEntity
import com.github.chsssssss.eonje.domain.model.DiscoveredMedia

interface CachedMediaRepository {
    suspend fun findByShortcode(shortcode: String): CachedMediaEntity?

    /** 계정의 캐시를 최신 [media]로 교체하고 계정당 최근 50건만 남긴다. */
    suspend fun replaceForAccount(username: String, media: List<DiscoveredMedia>)

    /** 오래된 게시물 단건 조회 결과를 캐시에 추가한다 — pruneToRecent를 타지 않아 바로 밀려나지 않는다. */
    suspend fun cacheSingle(username: String, media: DiscoveredMedia)
}
