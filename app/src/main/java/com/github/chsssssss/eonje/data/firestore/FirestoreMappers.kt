package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.google.firebase.firestore.DocumentSnapshot

/**
 * 도메인 모델 ↔ Firestore 문서. 필드는 iOS 앱에서도 같은 구조로 쓸 수 있게 플랫폼 중립적으로 둔다:
 * enum은 이름 문자열, 시간은 기기에서 만든 밀리초(Long), 색상은 ARGB 정수. 문서 ID는 기존 UUID 그대로다.
 *
 * 제외한 것: memo(기능 제거됨), thumbnailUrl(file:// 경로는 기기마다 달라서 저장하지 않는다 — 읽을 때 로컬 파일로 채운다).
 */
internal object Fields {
    // posts
    const val INSTAGRAM_URL = "instagramUrl"
    const val SHORTCODE = "shortcode"
    const val CAPTION = "caption"
    const val STATUS = "status"
    const val EXTRACTED_COUNT = "extractedCount"
    const val CREATED_AT = "createdAt"
    const val UNRESOLVED_REASON = "unresolvedReason"
    const val PLACE_IDS = "placeIds"
    const val CANDIDATES = "candidates"

    // places
    const val NAME = "name"
    const val ADDRESS = "address"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val KAKAO_PLACE_ID = "kakaoPlaceId"
    const val CATEGORY = "category"
    const val RESOLVED_AT = "resolvedAt"
    const val FOLDER_ID = "folderId"

    // folders
    const val COLOR = "color"
    const val ICON_KEY = "iconKey"

    // candidates 배열 안의 항목
    const val EXTRACTION_INDEX = "extractionIndex"
    const val EXTRACTED_NAME = "extractedName"
    const val RANK = "rank"
}

// ── SavedPost ──

/** save()가 쓰는 필드 이름들. placeIds·candidates는 여기 없다 — 게시물을 다시 저장해도 그 값을 건드리지 않으려고 mergeFields로 쓴다. */
internal val POST_DOMAIN_FIELDS = listOf(
    Fields.INSTAGRAM_URL, Fields.SHORTCODE, Fields.CAPTION, Fields.STATUS,
    Fields.EXTRACTED_COUNT, Fields.CREATED_AT, Fields.UNRESOLVED_REASON,
)

internal fun SavedPost.toFields(): Map<String, Any?> = mapOf(
    Fields.INSTAGRAM_URL to instagramUrl,
    Fields.SHORTCODE to shortcode,
    Fields.CAPTION to caption,
    Fields.STATUS to status.name,
    Fields.EXTRACTED_COUNT to extractedCount,
    Fields.CREATED_AT to createdAt,
    Fields.UNRESOLVED_REASON to unresolvedReason?.name,
)

/** [localThumbnailUrl]은 이 기기의 {postId}.jpg가 있으면 그 file:// 경로, 없으면 null이다. 필수 필드가 없는 문서는 건너뛴다(null). */
internal fun DocumentSnapshot.toSavedPost(localThumbnailUrl: (String) -> String?): SavedPost? {
    val url = getString(Fields.INSTAGRAM_URL) ?: return null
    return SavedPost(
        id = id,
        shortcode = getString(Fields.SHORTCODE),
        instagramUrl = url,
        caption = getString(Fields.CAPTION),
        thumbnailUrl = localThumbnailUrl(id),
        status = enumOrDefault(getString(Fields.STATUS), ResolveStatus.PENDING),
        extractedCount = getLong(Fields.EXTRACTED_COUNT)?.toInt() ?: 0,
        createdAt = getLong(Fields.CREATED_AT) ?: 0L,
        unresolvedReason = getString(Fields.UNRESOLVED_REASON)?.let { enumOrNull<UnresolvedReason>(it) },
    )
}

internal fun DocumentSnapshot.placeIds(): List<String> =
    (get(Fields.PLACE_IDS) as? List<*>)?.filterIsInstance<String>().orEmpty()

// ── Place ──

internal fun Place.toFields(): Map<String, Any?> = mapOf(
    Fields.NAME to name,
    Fields.ADDRESS to address,
    Fields.LATITUDE to latitude,
    Fields.LONGITUDE to longitude,
    Fields.KAKAO_PLACE_ID to kakaoPlaceId,
    Fields.CATEGORY to category,
    Fields.STATUS to status.name,
    Fields.CREATED_AT to createdAt,
    Fields.RESOLVED_AT to resolvedAt,
    Fields.FOLDER_ID to folderId,
)

internal fun DocumentSnapshot.toPlace(): Place = Place(
    id = id,
    name = getString(Fields.NAME),
    address = getString(Fields.ADDRESS),
    latitude = getDouble(Fields.LATITUDE),
    longitude = getDouble(Fields.LONGITUDE),
    kakaoPlaceId = getString(Fields.KAKAO_PLACE_ID),
    category = getString(Fields.CATEGORY),
    status = enumOrDefault(getString(Fields.STATUS), ResolveStatus.PENDING),
    createdAt = getLong(Fields.CREATED_AT) ?: 0L,
    resolvedAt = getLong(Fields.RESOLVED_AT),
    folderId = getString(Fields.FOLDER_ID),
)

// ── Folder ──

internal fun Folder.toFields(): Map<String, Any?> = mapOf(
    Fields.NAME to name,
    Fields.COLOR to color,
    Fields.ICON_KEY to iconKey,
    Fields.CREATED_AT to createdAt,
)

internal fun DocumentSnapshot.toFolder(): Folder? {
    val name = getString(Fields.NAME) ?: return null
    return Folder(
        id = id,
        name = name,
        createdAt = getLong(Fields.CREATED_AT) ?: 0L,
        color = getLong(Fields.COLOR)?.toInt() ?: FolderVisuals.DEFAULT_COLOR,
        iconKey = getString(Fields.ICON_KEY) ?: FolderVisuals.DEFAULT_ICON,
    )
}

// ── ExtractedCandidate: 게시물 문서의 candidates 배열 안에 들어간다 ──

internal fun ExtractedCandidate.toMap(): Map<String, Any?> = mapOf(
    Fields.EXTRACTION_INDEX to extractionIndex,
    Fields.EXTRACTED_NAME to extractedName,
    Fields.RANK to rank,
    Fields.KAKAO_PLACE_ID to place.kakaoPlaceId,
    Fields.NAME to place.name,
    Fields.ADDRESS to place.address,
    Fields.CATEGORY to place.category,
    Fields.LATITUDE to place.latitude,
    Fields.LONGITUDE to place.longitude,
)

internal fun DocumentSnapshot.toCandidates(): List<ExtractedCandidate> =
    (get(Fields.CANDIDATES) as? List<*>).orEmpty().mapNotNull { raw ->
        val m = raw as? Map<*, *> ?: return@mapNotNull null
        ExtractedCandidate(
            postId = id,
            extractionIndex = (m[Fields.EXTRACTION_INDEX] as? Number)?.toInt() ?: return@mapNotNull null,
            extractedName = m[Fields.EXTRACTED_NAME] as? String ?: return@mapNotNull null,
            rank = (m[Fields.RANK] as? Number)?.toInt() ?: return@mapNotNull null,
            place = PlaceCandidate(
                kakaoPlaceId = m[Fields.KAKAO_PLACE_ID] as? String ?: return@mapNotNull null,
                name = m[Fields.NAME] as? String ?: return@mapNotNull null,
                address = m[Fields.ADDRESS] as? String ?: "",
                category = m[Fields.CATEGORY] as? String,
                latitude = (m[Fields.LATITUDE] as? Number)?.toDouble() ?: return@mapNotNull null,
                longitude = (m[Fields.LONGITUDE] as? Number)?.toDouble() ?: return@mapNotNull null,
            ),
        )
    }.sortedWith(compareBy({ it.extractionIndex }, { it.rank }))

private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
    name?.let { n -> enumValues<E>().firstOrNull { it.name == n } }

/** 알 수 없는 값(다른 앱 버전이 쓴 새 상태 등)이 와도 앱이 죽지 않게 기본값으로 읽는다. */
private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E = enumOrNull<E>(name) ?: default
