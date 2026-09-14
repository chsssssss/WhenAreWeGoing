package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.InstagramPostMeta

/**
 * F2: 게시물 URL만으로 캡션 원문과 대표 이미지를 얻는다.
 * 서버(Firebase Function)가 Apify로 대신 조회해서 계정명·캡션 원문을 돌려준다.
 */
interface InstagramMetaLookupRepository {
    suspend fun fetchMeta(url: String): Result<InstagramPostMeta?>
}
