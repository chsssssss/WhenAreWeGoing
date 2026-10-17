package com.github.chsssssss.eonje.domain.usecase

import android.os.SystemClock
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.ThumbnailStore
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
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent
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
    private val thumbnailStore: ThumbnailStore,
    private val notifier: PlaceSavedNotifier,
    private val analytics: FirebaseAnalytics,
) {
    suspend operator fun invoke(postId: String): MatchPostResult {
        val post = savedPostRepository.findById(postId) ?: return MatchPostResult.Done

        // 이전 시도에서 캡션을 이미 받아뒀으면(파싱·카카오 검색 단계에서 재시도 중) Apify를 다시 부르지 않는다 —
        // 호출할 때마다 크레딧이 들고, 받아온 내용도 그대로다. 빈 캡션은 저장 전에 UNRESOLVED로 끝나므로
        // 여기 남아 있는 캡션은 항상 Apify에서 받은 값이다.
        val cachedCaption = post.caption
        val fetched = if (!cachedCaption.isNullOrBlank()) {
            FetchedPost(cachedCaption, post.thumbnailUrl)
        } else {
            when (val result = fetchFromApify(postId, post.instagramUrl)) {
                FetchOutcome.Failed -> return MatchPostResult.Retry
                FetchOutcome.NoCaption -> return unresolved(postId, UnresolvedReason.PLACE_NOT_FOUND)
                is FetchOutcome.Fetched -> result.post
            }
        }
        val caption = fetched.caption
        val thumbnailUrl = fetched.thumbnailUrl
        // 2차(이미지) 파싱엔 원격 URL 대신 기기에 받아둔 썸네일을 쓴다 — 재시도 시점엔 CDN URL이 만료됐을 수 있다.
        val mediaUrls = listOfNotNull(thumbnailUrl)

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
            thumbnailUrl = thumbnailUrl,
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

    private data class FetchedPost(val caption: String, val thumbnailUrl: String?)

    private sealed interface FetchOutcome {
        data object Failed : FetchOutcome
        data object NoCaption : FetchOutcome
        data class Fetched(val post: FetchedPost) : FetchOutcome
    }

    /** fetchInstagramMeta(Apify)로 캡션·대표 이미지를 받아 썸네일을 기기에 저장하고 게시물에 기록한다. */
    private suspend fun fetchFromApify(postId: String, instagramUrl: String): FetchOutcome {
        val fetchStartedAt = SystemClock.elapsedRealtime()
        val metaResult = metaLookupRepository.fetchMeta(instagramUrl)
        analytics.logEvent(Events.APIFY_FETCH) {
            param(
                Events.Param.STATUS,
                when {
                    metaResult.isFailure -> Events.Status.ERROR
                    metaResult.getOrNull() == null -> Events.Status.EMPTY
                    else -> Events.Status.SUCCESS
                },
            )
            param(Events.Param.LATENCY_MS, SystemClock.elapsedRealtime() - fetchStartedAt)
        }
        if (metaResult.isFailure) return FetchOutcome.Failed
        val meta = metaResult.getOrNull() ?: return FetchOutcome.NoCaption
        val caption = meta.caption
        if (caption.isNullOrBlank()) return FetchOutcome.NoCaption
        // 인스타 CDN의 media_url은 시간이 지나면 만료되므로, 나중에도 계속 보이도록 기기에 받아둔다.
        val thumbnailUrl = meta.imageUrl?.let { thumbnailStore.cache(postId, it) }

        savedPostRepository.updateExtraction(
            id = postId,
            caption = caption,
            thumbnailUrl = thumbnailUrl,
            extractedCount = 0,
        )
        return FetchOutcome.Fetched(FetchedPost(caption, thumbnailUrl))
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
        analytics.logEvent(Events.POST_RESOLVED) { param(Events.Param.SOURCE, Events.Source.AUTO) }
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
