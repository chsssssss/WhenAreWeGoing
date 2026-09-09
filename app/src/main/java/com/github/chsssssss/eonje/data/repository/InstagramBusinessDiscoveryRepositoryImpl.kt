package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.data.remote.instagram.InstagramBusinessDiscoveryApi
import com.github.chsssssss.eonje.data.remote.instagram.MediaItemDto
import com.github.chsssssss.eonje.domain.model.DiscoveredAccount
import com.github.chsssssss.eonje.domain.model.DiscoveredMedia
import com.github.chsssssss.eonje.domain.repository.InstagramBusinessDiscoveryRepository
import com.github.chsssssss.eonje.domain.util.InstagramUrlParser
import kotlinx.coroutines.CancellationException
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ")

class InstagramBusinessDiscoveryRepositoryImpl @Inject constructor(
    private val api: InstagramBusinessDiscoveryApi,
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
                    IllegalStateException(response.error?.message ?: "발견할 수 없는 계정이에요. 프로페셔널 계정인지 확인해주세요")
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
        } catch (e: Exception) {
            Result.failure(e)
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
