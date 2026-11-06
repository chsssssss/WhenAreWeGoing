package com.github.chsssssss.eonje.data.migration

import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.util.InstagramUrlParser
import com.github.chsssssss.eonje.domain.util.TextLimit

/**
 * firestore.rules의 상한. 규칙 파일과 같은 값이어야 한다(규칙을 바꾸면 여기도 바꾼다 — 이전 테스트가 상한 경계값 문서를 규칙에
 * 그대로 써서 어긋나면 깨진다).
 */
internal object FirestoreLimits {
    const val FOLDER_NAME = 100
    const val FOLDER_ICON = 32
    const val PLACE_NAME = 200
    const val PLACE_ADDRESS = 300
    const val PLACE_CATEGORY = 200
    const val POST_CAPTION = 20_000
    const val POST_URL = 500
}

/** [fixedFields]는 고친 필드 **이름**이다(값은 남기지 않는다). 비어 있으면 고친 게 없다. */
internal data class Sanitized<T>(val value: T, val fixedFields: List<String>) {
    val changed: Boolean get() = fixedFields.isNotEmpty()
}

/**
 * 옛 Room 데이터를 이전할 때, 규칙 상한을 넘는 값을 **안전하게 고칠 수 있는 것만** 고쳐서 옮긴다. 고치지 못하는 건 그대로 두고
 * (규칙이 거부하면 이전기가 그 문서만 건너뛴다). Room의 원본은 건드리지 않는다 — 고친 값은 Firestore로 가는 사본에만 반영된다.
 *
 * 고치는 것(표시용 텍스트라 잘라도 문서의 의미가 안 바뀐다):
 * - 폴더 이름: 비어 있거나 공백뿐이면 "이름 없는 폴더", 100자를 넘으면 100자로 자른다. 폴더 아이콘이 32자를 넘으면 기본 아이콘.
 * - 장소 이름(200자), 주소(300자), 카테고리(200자): 상한까지 자른다.
 * - 게시물 캡션: 20,000자까지 자른다(인스타 캡션 상한은 2,200자라 실제로는 거의 없다).
 * - 게시물 URL이 500자를 넘고 인스타 게시물 URL로 해석되면 정규형(`https://www.instagram.com/{p|reel|tv}/{code}/`)으로 바꾼다.
 *   정규형도 500자를 넘으면(코드가 비정상적으로 긴 경우) 고치지 않는다.
 *
 * 고치지 않는 것(자르거나 바꾸면 데이터의 의미가 달라진다):
 * - 식별자: kakaoPlaceId, shortcode, folderId, 문서 ID(잘라내면 다른 값이 되어 중복 판단·연결이 깨진다)
 * - 좌표 범위 밖 값, 상태·사유 값, 게시물의 extractedCount 상한 초과, placeIds·candidates 크기 초과
 */
internal object LegacyDocSanitizer {
    const val UNNAMED_FOLDER = "이름 없는 폴더"

    fun folder(folder: Folder): Sanitized<Folder> {
        val fixed = ArrayList<String>()
        var name = folder.name
        if (name.isBlank()) {
            name = UNNAMED_FOLDER
            fixed += "name"
        } else if (name.length > FirestoreLimits.FOLDER_NAME) {
            name = TextLimit.safeTake(name.trim(), FirestoreLimits.FOLDER_NAME)
            fixed += "name"
        }
        var icon = folder.iconKey
        if (icon.length > FirestoreLimits.FOLDER_ICON) {
            icon = FolderVisuals.DEFAULT_ICON
            fixed += "iconKey"
        }
        return Sanitized(if (fixed.isEmpty()) folder else folder.copy(name = name, iconKey = icon), fixed)
    }

    fun place(place: Place): Sanitized<Place> {
        val fixed = ArrayList<String>()
        fun cut(field: String, value: String?, max: Int): String? {
            if (value == null || value.length <= max) return value
            fixed += field
            return TextLimit.safeTake(value, max)
        }
        val name = cut("name", place.name, FirestoreLimits.PLACE_NAME)
        val address = cut("address", place.address, FirestoreLimits.PLACE_ADDRESS)
        val category = cut("category", place.category, FirestoreLimits.PLACE_CATEGORY)
        return Sanitized(if (fixed.isEmpty()) place else place.copy(name = name, address = address, category = category), fixed)
    }

    fun post(post: SavedPost): Sanitized<SavedPost> {
        val fixed = ArrayList<String>()
        var caption = post.caption
        if (caption != null && caption.length > FirestoreLimits.POST_CAPTION) {
            caption = TextLimit.safeTake(caption, FirestoreLimits.POST_CAPTION)
            fixed += "caption"
        }
        var url = post.instagramUrl
        if (url.length > FirestoreLimits.POST_URL) {
            val canonical = InstagramUrlParser.parsePostUrl(url)?.canonicalUrl
            if (canonical != null && canonical.length <= FirestoreLimits.POST_URL) {
                url = canonical
                fixed += "instagramUrl"
            }
        }
        return Sanitized(if (fixed.isEmpty()) post else post.copy(caption = caption, instagramUrl = url), fixed)
    }
}
