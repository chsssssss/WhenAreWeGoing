package com.github.chsssssss.eonje.domain.usecase

import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.ExtractedPlace
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.notification.PlaceSavedNotifier
import com.github.chsssssss.eonje.domain.repository.CachedMediaRepository
import com.github.chsssssss.eonje.domain.repository.CaptionParsingRepository
import com.github.chsssssss.eonje.domain.repository.ExtractedCandidateRepository
import com.github.chsssssss.eonje.domain.repository.KakaoLocalRepository
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.SavedPostRepository
import java.util.UUID
import javax.inject.Inject

private const val MAX_CANDIDATES_PER_ITEM = 5

/**
 * F2: shortcode 캐시 조회 → 캡션(+이미지) LLM 파싱 → 카카오 검색 → 상태 결정.
 * 실패는 UNRESOLVED로 조용히 종료한다 — 이 파이프라인은 절대 예외를 던지지 않는다.
 */
class MatchPostUseCase @Inject constructor(
    private val savedPostRepository: SavedPostRepository,
    private val cachedMediaRepository: CachedMediaRepository,
    private val captionParsingRepository: CaptionParsingRepository,
    private val kakaoLocalRepository: KakaoLocalRepository,
    private val placeRepository: PlaceRepository,
    private val extractedCandidateRepository: ExtractedCandidateRepository,
    private val notifier: PlaceSavedNotifier,
) {
    suspend operator fun invoke(postId: String) {
        val post = savedPostRepository.findById(postId) ?: return
        val shortcode = post.shortcode ?: return unresolved(postId)

        val cached = cachedMediaRepository.findByShortcode(shortcode) ?: return unresolved(postId)
        val caption = cached.caption
        if (caption.isNullOrBlank()) return unresolved(postId)

        savedPostRepository.updateExtraction(
            id = postId,
            caption = caption,
            thumbnailUrl = cached.mediaUrls.firstOrNull(),
            extractedCount = 0,
        )

        // 1차 파싱: 캡션만
        var extracted = captionParsingRepository.extractPlaces(caption).getOrNull().orEmpty()

        // 2차 파싱: 결과 없고 이미지가 있으면(영상 제외) 이미지 포함 재시도
        if (extracted.isEmpty() && cached.mediaUrls.isNotEmpty() && cached.mediaType != "VIDEO") {
            extracted = captionParsingRepository.extractPlaces(caption, cached.mediaUrls).getOrNull().orEmpty()
        }

        if (extracted.isEmpty()) return unresolved(postId)

        savedPostRepository.updateExtraction(
            id = postId,
            caption = caption,
            thumbnailUrl = cached.mediaUrls.firstOrNull(),
            extractedCount = extracted.size,
        )

        val searched = extracted.map { place -> place to searchCandidates(place) }
        if (searched.all { (_, candidates) -> candidates.isEmpty() }) return unresolved(postId)

        // 추출 1건 + 검색 1건 → 자동 확정. 그 외(추출 2건 이상, 후보 다수)는 무조건 다중 모드.
        if (extracted.size == 1) {
            val candidates = searched.first().second
            when {
                candidates.isEmpty() -> return unresolved(postId)
                candidates.size == 1 -> return autoResolve(postId, candidates.first())
            }
        }

        saveCandidatesForReview(postId, searched)
    }

    private suspend fun searchCandidates(place: ExtractedPlace): List<PlaceCandidate> {
        val query = listOfNotNull(place.region, place.name).joinToString(" ")
        return kakaoLocalRepository.searchPlaces(query).getOrNull().orEmpty()
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
        savedPostRepository.updateStatus(postId, ResolveStatus.RESOLVED)
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
        savedPostRepository.updateStatus(postId, ResolveStatus.NEEDS_REVIEW)
    }

    private suspend fun unresolved(postId: String) {
        savedPostRepository.updateStatus(postId, ResolveStatus.UNRESOLVED)
    }
}
