package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.data.remote.instagram.InstagramBusinessDiscoveryApi
import com.github.chsssssss.eonje.data.remote.instagram.MediaItemDto
import com.github.chsssssss.eonje.domain.model.DiscoveredAccount
import com.github.chsssssss.eonje.domain.model.DiscoveredMedia
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
import com.github.chsssssss.eonje.data.remote.instagram.BusinessDiscoveryResponse
import com.github.chsssssss.eonje.domain.repository.InstagramTokenExpiredException
import com.github.chsssssss.eonje.domain.util.InstagramUrlParser
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

// https://developers.facebook.com/docs/graph-api/guides/error-handling — 190 = 만료되었거나 무효한 OAuth 액세스 토큰.
private const val OAUTH_ERROR_CODE = 190

class InstagramBusinessDiscoveryRepositoryImpl @Inject constructor(
    private val api: InstagramBusinessDiscoveryApi,
    private val json: Json,
) : InstagramBusinessDiscoveryRepository {

    override suspend fun discover(username: String): Result<DiscoveredAccount> {
        val myIgUserId = BuildConfig.IG_BUSINESS_ACCOUNT_ID
        val accessToken = BuildConfig.IG_ACCESS_TOKEN
        if (myIgUserId.isBlank() || accessToken.isBlank()) {
            return Result.failure(IllegalStateException("인스타그램 연동 토큰이 설정되지 않았어요"))
        }
        return try {
            val response = api.getBusinessDiscovery(
                myIgUserId = myIgUserId,
                fields = InstagramBusinessDiscoveryApi.fields(username),
                accessToken = accessToken,
            )
            val discovery = response.businessDiscovery
                ?: return Result.failure(
                    if (response.error?.code == OAUTH_ERROR_CODE) {
                        InstagramTokenExpiredException(response.error.message ?: "인스타그램 연동 토큰이 만료됐어요")
                    } else {
                        IllegalStateException(response.error?.message ?: "발견할 수 없는 계정이에요. 프로페셔널 계정인지 확인해주세요")
                    }
                )
            val igUserId = discovery.id
                ?: return Result.failure(IllegalStateException("계정 정보를 확인할 수 없어요"))

            Result.success(
                DiscoveredAccount(
                    username = discovery.username ?: username,
                    igUserId = igUserId,
                    profileImageUrl = discovery.profilePictureUrl,
                    media = discovery.media?.data.orEmpty().mapNotNull { it.toDiscoveredMediaOrNull() },
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            Result.failure(e.toDomainException())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Graph API는 만료/무효 토큰을 200이 아닌 HTTP 에러 상태로 응답하므로, 에러 바디를 직접 들여다봐야 한다.
    private fun HttpException.toDomainException(): Exception {
        val errorCode = runCatching {
            response()?.errorBody()?.string()
                ?.let { json.decodeFromString<BusinessDiscoveryResponse>(it) }
                ?.error?.code
        }.getOrNull()
        return if (errorCode == OAUTH_ERROR_CODE) {
            InstagramTokenExpiredException("인스타그램 연동 토큰이 만료됐어요")
        } else {
            this
        }
    }

    private fun MediaItemDto.toDiscoveredMediaOrNull(): DiscoveredMedia? {
        val shortcode = InstagramUrlParser.extractShortcode(permalink) ?: return null
        val mediaUrls = children?.data?.mapNotNull { it.mediaUrl }?.takeIf { it.isNotEmpty() }
            ?: listOfNotNull(mediaUrl)
        val timestampMillis = runCatching {
            OffsetDateTime.parse(timestamp, TIMESTAMP_FORMATTER).toInstant().toEpochMilli()
        }.getOrDefault(System.currentTimeMillis())

        return DiscoveredMedia(
            shortcode = shortcode,
            caption = caption,
            permalink = permalink,
            mediaType = mediaType,
            mediaUrls = mediaUrls,
            timestamp = timestampMillis,
        )
    }
}
