package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.data.remote.kakao.KakaoLocalApi
import com.github.chsssssss.eonje.data.remote.kakao.KakaoPlaceDocument
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class KakaoLocalRepositoryImpl @Inject constructor(
    private val api: KakaoLocalApi,
) : KakaoLocalRepository {

    override suspend fun searchPlaces(query: String): Result<List<PlaceCandidate>> {
        val apiKey = BuildConfig.KAKAO_REST_API_KEY
        if (apiKey.isBlank()) {
            return Result.failure(IllegalStateException("카카오 API 키가 설정되지 않았어요"))
        }
        return try {
            val response = api.searchKeyword(
                authorization = "KakaoAK $apiKey",
                query = query,
            )
            Result.success(response.documents.map { it.toPlaceCandidate() })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun KakaoPlaceDocument.toPlaceCandidate() = PlaceCandidate(
        kakaoPlaceId = id,
        name = placeName,
        address = roadAddressName?.takeIf { it.isNotBlank() } ?: addressName,
        category = categoryGroupName?.takeIf { it.isNotBlank() } ?: categoryName,
        latitude = y.toDouble(),
        longitude = x.toDouble(),
    )
}
