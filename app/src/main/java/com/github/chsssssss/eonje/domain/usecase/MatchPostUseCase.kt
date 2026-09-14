package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.ExtractedPlace
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.notification.PlaceSavedNotifier
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.InstagramMetaLookupRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

private const val MAX_CANDIDATES_PER_ITEM = 5

sealed interface MatchPostResult {
    /** 확정/보류/미확정 중 하나로 종결됨 — 더 이상 재시도할 필요 없음. */
    data object Done : MatchPostResult
    /** 네트워크 문제로 판단을 내리지 못함 — WorkManager가 지수 백오프로 재시도해야 함. */
    data object Retry : MatchPostResult
}

/**
 * F2: 게시물 URL → (Firebase Function `fetchInstagramMeta` → Apify) 캡션 조회 → LLM 파싱 →
 * 카카오 검색 → 상태 결정. 네트워크 실패는 [MatchPostResult.Retry]로 재시도를 요청하고,
 * 그 외 실패는 UNRESOLVED로 조용히 종료한다.
 */
class MatchPostUseCase @Inject constructor(
    private val savedPostRepository: SavedPostRepository,
    private val metaLookupRepository: InstagramMetaLookupRepository,
    private val captionParsingRepository: CaptionParsingRepository,
    private val kakaoLocalRepository: KakaoLocalRepository,
    private val placeRepository: PlaceRepository,
    private val extractedCandidateRepository: ExtractedCandidateRepository,
    private val notifier: PlaceSavedNotifier,
) {
    suspend operator fun invoke(postId: String): MatchPostResult {
        val post = savedPostRepository.findById(postId) ?: return MatchPostResult.Done

        val metaResult = metaLookupRepository.fetchMeta(post.instagramUrl)
        if (metaResult.isFailure) return MatchPostResult.Retry
        val meta = metaResult.getOrNull() ?: return unresolved(postId, UnresolvedReason.PLACE_NOT_FOUND)
        val caption = meta.caption
        if (caption.isNullOrBlank()) return unresolved(postId, UnresolvedReason.PLACE_NOT_FOUND)
        val mediaUrls = listOfNotNull(meta.imageUrl)

        savedPostRepository.updateExtraction(
            id = postId,
            caption = caption,
            thumbnailUrl = mediaUrls.firstOrNull(),
            extractedCount = 0,
        )

        // 1차 파싱: 캡션만
        val firstPass = captionParsingRepository.extractPlaces(caption)
        if (firstPass.isRetryableFailure()) return MatchPostResult.Retry
        var extracted = firstPass.getOrNull().orEmpty()

        // 2차 파싱: 결과 없고 이미지가 있으면 이미지 포함 재시도
        if (extracted.isEmpty() && mediaUrls.isNotEmpty()) {
            val secondPass = captionParsingRepository.extractPlaces(caption, mediaUrls)
            if (secondPass.isRetryableFailure()) return MatchPostResult.Retry
            extracted = secondPass.getOrNull().orEmpty()
        }

        if (extracted.isEmpty()) return unresolved(postId, UnresolvedReason.PLACE_NOT_FOUND)

        savedPostRepository.updateExtraction(
            id = postId,
            caption = caption,
            thumbnailUrl = mediaUrls.firstOrNull(),
            extractedCount = extracted.size,
        )

        val searched = mutableListOf<Pair<ExtractedPlace, List<PlaceCandidate>>>()
        var usedLooseSearch = false
        for (place in extracted) {
            val result = searchCandidates(place)
            if (result.isRetryableFailure()) return MatchPostResult.Retry
            val outcome = result.getOrNull()
            if (outcome?.isLoose == true) usedLooseSearch = true
            searched += place to outcome?.candidates.orEmpty()
        }
        if (searched.all { (_, candidates) -> candidates.isEmpty() }) {
            return unresolved(postId, UnresolvedReason.PLACE_NOT_FOUND)
        }

        // 추출 1건 + 검색 1건 → 자동 확정. 그 외(추출 2건 이상, 후보 다수)는 무조건 다중 모드.
        if (extracted.size == 1) {
            val candidates = searched.first().second
            when {
                candidates.isEmpty() -> return unresolved(postId, UnresolvedReason.PLACE_NOT_FOUND)
                // 느슨한 검색(상호명만)으로 건진 결과는 같은 브랜드의 다른 지점일 수 있어서
                // 딱 1건이어도 자동 확정하지 않고 사용자에게 확인받는다.
                candidates.size == 1 && !usedLooseSearch -> {
                    autoResolve(postId, candidates.first())
                    return MatchPostResult.Done
                }
            }
        }

        saveCandidatesForReview(postId, searched)
        return MatchPostResult.Done
    }

    /** 앱 재실행 시 재매칭을 포기하고 조용히 UNRESOLVED로 넘길 때 쓴다 (재시도 횟수 초과). */
    suspend fun markUnresolved(postId: String) {
        unresolved(postId, UnresolvedReason.NETWORK_ERROR)
    }

    /** [isLoose]는 지역까지 맞춘 정확한 결과가 아니라 상호명만으로 느슨하게 건진 결과라는 표시다. */
    private data class SearchOutcome(val candidates: List<PlaceCandidate>, val isLoose: Boolean)

    private suspend fun searchCandidates(place: ExtractedPlace): Result<SearchOutcome> {
        val region = place.region?.takeIf { it.isNotBlank() }
        val precise = kakaoLocalRepository.searchPlaces(listOfNotNull(region, place.name).joinToString(" "))
        if (region == null || precise.isFailure || precise.getOrNull()?.isNotEmpty() == true) {
            return precise.map { SearchOutcome(it, isLoose = false) }
        }

        // 지역과 상호명을 전부 이어붙이면("춘천시 후평동 반마리닭국수 후평점") 카카오가 0건을 주는 일이 잦다.
        // 상호명만으로 다시 찾되, 같은 이름의 다른 도시 가게가 딸려오지 않도록 지역명으로 걸러낸다.
        val regionKeyword = region.substringBefore(' ')
        return kakaoLocalRepository.searchPlaces(place.name)
            .map { candidates ->
                SearchOutcome(candidates.filter { it.address.contains(regionKeyword) }, isLoose = true)
            }
    }

    // IOException(네트워크 자체 실패)뿐 아니라, 서버 쪽 일시 오류(5xx)·호출 제한(429)도 재시도 대상으로 본다 —
    // 그 외 HTTP 에러(4xx 등)는 다시 불러도 똑같이 실패할 가능성이 높아 바로 UNRESOLVED로 종결한다.
    private fun <T> Result<T>.isRetryableFailure(): Boolean {
        val exception = exceptionOrNull() ?: return false
        if (exception is IOException) return true
        if (exception is HttpException) {
            val code = exception.code()
            return code == 429 || code >= 500
        }
        return false
    }

    private suspend fun autoResolve(postId: String, candidate: PlaceCandidate) {
        val now = System.currentTimeMillis()
        val placeId = placeRepository.save(
            PlaceEntity(
                id = UUID.randomUUID().toString(),
                name = candidate.name,
                address = candidate.address,
                latitude = candidate.latitude,
                longitude = candidate.longitude,
                kakaoPlaceId = candidate.kakaoPlaceId,
                category = candidate.category,
                memo = null,
                status = ResolveStatus.RESOLVED,
                createdAt = now,
                resolvedAt = now,
            )
        )
        placeRepository.linkPostToPlace(postId, placeId)
        savedPostRepository.updateStatus(postId, ResolveStatus.RESOLVED, unresolvedReason = null)
        notifier.notifyResolved(candidate.name)
    }

    private suspend fun saveCandidatesForReview(
        postId: String,
        searched: List<Pair<ExtractedPlace, List<PlaceCandidate>>>,
    ) {
        val entities = searched.flatMapIndexed { index, (place, candidates) ->
            candidates.take(MAX_CANDIDATES_PER_ITEM).mapIndexed { rank, candidate ->
                ExtractedCandidateEntity(
                    postId = postId,
                    extractionIndex = index,
                    extractedName = place.name,
                    rank = rank,
                    kakaoPlaceId = candidate.kakaoPlaceId,
                    name = candidate.name,
                    address = candidate.address,
                    category = candidate.category,
                    latitude = candidate.latitude,
                    longitude = candidate.longitude,
                )
            }
        }
        extractedCandidateRepository.replaceForPost(postId, entities)
        savedPostRepository.updateStatus(postId, ResolveStatus.NEEDS_REVIEW, unresolvedReason = null)
    }

    private suspend fun unresolved(postId: String, reason: UnresolvedReason): MatchPostResult {
        savedPostRepository.updateStatus(postId, ResolveStatus.UNRESOLVED, reason)
        return MatchPostResult.Done
    }
}
