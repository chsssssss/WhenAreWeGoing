package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.ExtractedPlace

interface CaptionParsingRepository {
    /**
     * 캡션(과 선택적으로 이미지)에서 맛집 정보를 추출한다.
     * [imageUrls]가 주어지면 2차 파싱(이미지 포함), 없으면 1차 파싱(캡션만).
     */
    suspend fun extractPlaces(caption: String, imageUrls: List<String> = emptyList()): Result<List<ExtractedPlace>>
}
