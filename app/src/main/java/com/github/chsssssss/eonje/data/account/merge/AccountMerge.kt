package com.github.chsssssss.eonje.data.account.merge

import com.github.chsssssss.eonje.domain.util.ShareKey
import kotlinx.serialization.Serializable
import java.util.UUID

/*
 * 이미 다른 uid에 연결된 Google 계정으로 로그인(전환)할 때, 이 기기의 익명 데이터를 계정에 합치는 계획.
 * 이 파일은 Firestore·Android에 의존하지 않는 순수 코드다(단위 테스트 대상). 읽고 쓰는 쪽은 AccountMergeIo.kt.
 *
 * 저널 파일에 JSON으로 저장되므로 전부 @Serializable이다. 익명 데이터는 전환 뒤엔 읽을 수 없어서 전환 **전에** 스냅샷으로 남긴다.
 */

@Serializable
data class FolderSnap(val id: String, val name: String, val color: Int, val iconKey: String, val createdAt: Long)

@Serializable
data class PlaceSnap(
    val id: String,
    val name: String?,
    val address: String?,
    val latitude: Double?,
    val longitude: Double?,
    val kakaoPlaceId: String?,
    val category: String?,
    val status: String,
    val createdAt: Long,
    val resolvedAt: Long?,
    val folderId: String?,
    val postIds: List<String> = emptyList(),
)

/** 게시물 문서의 candidates 항목(인박스 항목이 후보를 잃지 않게 게시물과 함께 통째로 옮긴다). */
@Serializable
data class CandidateSnap(
    val extractionIndex: Int,
    val extractedName: String,
    val rank: Int,
    val kakaoPlaceId: String,
    val name: String,
    val address: String,
    val category: String?,
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class PostSnap(
    val id: String,
    val shortcode: String?,
    val instagramUrl: String,
    val caption: String?,
    val status: String,
    val extractedCount: Int,
    val createdAt: Long,
    val unresolvedReason: String?,
    val placeIds: List<String> = emptyList(),
    val candidates: List<CandidateSnap> = emptyList(),
)

/** 이 기기(익명 uid)의 데이터. 묘비(삭제된 장소)는 넣지 않는다. */
@Serializable
data class MergeSnapshot(
    val folders: List<FolderSnap> = emptyList(),
    val places: List<PlaceSnap> = emptyList(),
    val posts: List<PostSnap> = emptyList(),
) {
    val isEmpty: Boolean get() = folders.isEmpty() && places.isEmpty() && posts.isEmpty()
}

// ── 계정(합쳐질 쪽)의 현재 상태: 중복 판단에 필요한 만큼만 ──

data class TargetFolder(val id: String, val name: String)
data class TargetPlace(val id: String, val kakaoPlaceId: String?, val deleted: Boolean)

/** [key]는 [ShareKey.of](shortcode, url) — 문서 ID가 아니라 이 키로 같은 게시물인지 판단한다. */
data class TargetPost(val id: String, val key: String, val placeIds: Set<String>)
data class TargetSnapshot(
    val folders: List<TargetFolder> = emptyList(),
    val places: List<TargetPlace> = emptyList(),
    val posts: List<TargetPost> = emptyList(),
)

// ── 계획 ──

/** 계정에 이미 있는 장소에 연결할 게시물(장소의 postIds에 arrayUnion). */
@Serializable
data class PlaceLink(val placeId: String, val postIds: List<String>)

/** 버려지는 익명 게시물의 썸네일을, 살아남는 계정 쪽 게시물에 썸네일이 없을 때 그 이름으로 옮긴다. */
@Serializable
data class ThumbnailAdoption(val fromPostId: String, val toPostId: String)

/**
 * 한 번 계산해서 저널에 저장한다 — 재실행해도 같은 결과(멱등)가 되게. 쓰기는 folders → places(+links) → posts 순서이고
 * 전부 `set`/`arrayUnion`이라 끊긴 뒤 처음부터 다시 해도 중복·손실이 없다.
 */
@Serializable
data class MergePlan(
    /** 계정에 없는 이름의 폴더(새로 만든다). */
    val folderWrites: List<FolderSnap>,
    /** 새로 만들거나 되살릴(묘비) 장소 문서. id는 실제로 쓸 문서 ID, folderId·postIds는 이미 매핑돼 있다. */
    val placeWrites: List<PlaceSnap>,
    val placeLinks: List<PlaceLink>,
    /** 계정에 없는 게시물(candidates 포함 통째로). placeIds는 이미 매핑돼 있다. */
    val postWrites: List<PostSnap>,
    val thumbnailAdoptions: List<ThumbnailAdoption>,
    val discardedPostCount: Int,
)

object AccountMergePlanner {

    /**
     * - 폴더: 이름이 같으면 계정 쪽을 쓰고 장소의 folderId를 매핑한다.
     * - 장소: kakaoPlaceId가 같으면 계정 쪽을 쓴다(묘비면 되살린다). 아니면 새로 만든다. kakaoPlaceId가 없으면 항상 새로 만든다.
     * - 게시물: **문서 ID가 아니라 [ShareKey.of](shortcode, url)로** 같은 게시물인지 본다. 계정 쪽에 같은 키가 있으면 계정 쪽을 쓰고
     *   익명 쪽은 버린다. 그 게시물의 썸네일은 살아남는 쪽에 없을 때 옮긴다([ThumbnailAdoption]).
     * - 장소의 postIds와 게시물의 placeIds는 매핑표로 다시 쓰고, 둘이 서로 일관되게 유지한다. 버려진 게시물은 장소에 연결하지 않는다
     *   (계정 쪽 게시물을 건드리지 않기 때문 — 그 게시물이 이미 그 장소를 가리키고 있으면 장소 쪽에 이미 연결돼 있다).
     */
    fun plan(local: MergeSnapshot, target: TargetSnapshot): MergePlan {
        // ── 폴더 ──
        val folderIdByName = LinkedHashMap<String, String>().apply { target.folders.forEach { putIfAbsent(it.name, it.id) } }
        val targetFolderIds = target.folders.mapTo(HashSet()) { it.id }
        val folderMap = HashMap<String, String>()
        val folderWrites = ArrayList<FolderSnap>()
        local.folders.sortedWith(compareBy({ it.createdAt }, { it.id })).forEach { folder ->
            val existing = folderIdByName[folder.name]
            if (existing != null) {
                folderMap[folder.id] = existing
            } else {
                val id = if (folder.id in targetFolderIds) derivedId("merge-folder", folder.id) else folder.id
                folderMap[folder.id] = id
                folderIdByName[folder.name] = id
                folderWrites += folder.copy(id = id)
            }
        }

        // ── 게시물: 키로 중복 판단 ──
        val targetPostByKey = LinkedHashMap<String, TargetPost>().apply { target.posts.forEach { putIfAbsent(it.key, it) } }
        val survivorByKey = HashMap<String, PostSnap>()
        val survivors = ArrayList<PostSnap>()
        val adoptions = ArrayList<ThumbnailAdoption>()
        var discarded = 0
        local.posts.sortedBy { it.id }.forEach { post ->
            val key = ShareKey.of(post.shortcode, post.instagramUrl)
            val keptId = targetPostByKey[key]?.id ?: survivorByKey[key]?.id
            if (keptId != null) {
                discarded++
                if (keptId != post.id) adoptions += ThumbnailAdoption(fromPostId = post.id, toPostId = keptId)
            } else {
                survivorByKey[key] = post
                survivors += post
            }
        }
        val survivorIds = survivors.mapTo(HashSet()) { it.id }

        // ── 장소 ──
        val liveByKakao = HashMap<String, String>()
        val tombstoneByKakao = HashMap<String, String>()
        target.places.forEach { place ->
            val kakao = place.kakaoPlaceId ?: return@forEach
            if (place.deleted) tombstoneByKakao.putIfAbsent(kakao, place.id) else liveByKakao.putIfAbsent(kakao, place.id)
        }
        val targetPlaceIds = target.places.mapTo(HashSet()) { it.id }
        val placeMap = HashMap<String, String>()
        val plannedByKakao = HashMap<String, String>()
        val matchedLive = LinkedHashSet<String>()
        val writeSources = LinkedHashMap<String, PlaceSnap>() // 쓸 문서 ID → 내용의 출처
        local.places.sortedWith(compareBy({ it.createdAt }, { it.id })).forEach { place ->
            val kakao = place.kakaoPlaceId
            val already = kakao?.let { plannedByKakao[it] }
            val targetId = when {
                already != null -> already
                kakao != null && kakao in liveByKakao -> liveByKakao.getValue(kakao).also { matchedLive += it }
                kakao != null && kakao in tombstoneByKakao -> tombstoneByKakao.getValue(kakao).also { writeSources[it] = place }
                else -> (if (place.id in targetPlaceIds) derivedId("merge-place", place.id) else place.id).also { writeSources[it] = place }
            }
            if (kakao != null) plannedByKakao.putIfAbsent(kakao, targetId)
            placeMap[place.id] = targetId
        }

        // ── 게시물↔장소 연결을 매핑표로 다시 쓴다 (게시물의 placeIds를 기준으로, 장소의 postIds와 일관되게) ──
        val postsByTargetPlace = HashMap<String, LinkedHashSet<String>>()
        survivors.forEach { post ->
            post.placeIds.mapNotNull { placeMap[it] }.forEach { postsByTargetPlace.getOrPut(it) { LinkedHashSet() } += post.id }
        }
        local.places.forEach { place ->
            val targetId = placeMap[place.id] ?: return@forEach
            place.postIds.filter { it in survivorIds }.forEach { postsByTargetPlace.getOrPut(targetId) { LinkedHashSet() } += it }
        }
        // 게시물 쪽도 같은 연결을 갖도록 장소 쪽에서만 있던 연결을 게시물에 채운다.
        val survivorPlaceIds = HashMap<String, LinkedHashSet<String>>()
        survivors.forEach { post ->
            survivorPlaceIds[post.id] = LinkedHashSet(post.placeIds.mapNotNull { placeMap[it] })
        }
        postsByTargetPlace.forEach { (placeId, postIds) -> postIds.forEach { survivorPlaceIds.getValue(it) += placeId } }

        val placeWrites = writeSources.map { (id, source) ->
            source.copy(
                id = id,
                folderId = source.folderId?.let { folderMap[it] },
                postIds = postsByTargetPlace[id]?.toList().orEmpty(),
            )
        }
        val placeLinks = matchedLive.mapNotNull { id ->
            postsByTargetPlace[id]?.takeIf { it.isNotEmpty() }?.let { PlaceLink(id, it.toList()) }
        }
        val postWrites = survivors.map { post -> post.copy(placeIds = survivorPlaceIds.getValue(post.id).toList()) }

        return MergePlan(
            folderWrites = folderWrites,
            placeWrites = placeWrites,
            placeLinks = placeLinks,
            postWrites = postWrites,
            thumbnailAdoptions = adoptions,
            discardedPostCount = discarded,
        )
    }

    /** 문서 ID가 계정 쪽과 겹칠 때 쓰는 결정적 새 ID(다시 계산해도 같다). */
    private fun derivedId(namespace: String, id: String): String =
        UUID.nameUUIDFromBytes("$namespace:$id".toByteArray()).toString()
}
