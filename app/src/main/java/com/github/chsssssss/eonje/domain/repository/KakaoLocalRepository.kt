package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.PlaceCandidate

interface KakaoLocalRepository {
    suspend fun searchPlaces(query: String): Result<List<PlaceCandidate>>
}
