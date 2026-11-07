package com.github.chsssssss.eonje.data.account.merge

import com.github.chsssssss.eonje.data.firestore.Fields
import com.github.chsssssss.eonje.data.firestore.commitInChunksAndAwait
import com.github.chsssssss.eonje.data.firestore.folders
import com.github.chsssssss.eonje.data.firestore.isTombstone
import com.github.chsssssss.eonje.data.firestore.placeIds
import com.github.chsssssss.eonje.data.firestore.placeUpdate
import com.github.chsssssss.eonje.data.firestore.places
import com.github.chsssssss.eonje.data.firestore.posts
import com.github.chsssssss.eonje.data.firestore.toCandidates
import com.github.chsssssss.eonje.data.firestore.toFields
import com.github.chsssssss.eonje.data.firestore.toFolder
import com.github.chsssssss.eonje.data.firestore.toPlaceOrNull
import com.github.chsssssss.eonje.data.firestore.toSavedPost
import com.github.chsssssss.eonje.data.local.ThumbnailStore
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.github.chsssssss.eonje.domain.util.ShareKey
import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 합치기의 읽기·쓰기. 계획 계산은 [AccountMergePlanner](순수), 여기는 Firestore와 썸네일 파일을 만진다.
 * 문서 변환은 앱의 매퍼(`FirestoreMappers`)를 그대로 써서, 합쳐서 만든 문서가 새로 만든 문서와 같은 모양이 되게 한다.
 */
@Singleton
class AccountMergeIo @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val thumbnailStore: ThumbnailStore,
) {
    /** 이 기기(익명 uid)의 데이터. 전환 **전에** 읽어야 한다. 서버를 먼저 보고(연결은 어차피 온라인), 안 되면 캐시를 쓴다. */
    suspend fun readSnapshot(uid: String): MergeSnapshot {
        val folders = readAll(firestore.folders(uid)).mapNotNull { it.toFolder() }
            .map { FolderSnap(it.id, it.name, it.color, it.iconKey, it.createdAt) }
        val places = readAll(firestore.places(uid)).mapNotNull { it.toPlaceOrNull() }.map { it.toSnap() }
        val posts = readAll(firestore.posts(uid)).mapNotNull { doc ->
            val post = doc.toSavedPost { null } ?: return@mapNotNull null
            post.toSnap(doc.placeIds(), doc.toCandidates())
        }
        return MergeSnapshot(folders, places, posts)
    }

    /** 계정(전환한 uid)의 현재 상태에서 중복 판단에 필요한 것만 읽는다. 게시물은 이 기기 게시물의 키와 겹치는 것만 찾는다. */
    suspend fun readTarget(uid: String, local: MergeSnapshot): TargetSnapshot {
        val targetFolders = readServer(firestore.folders(uid)).mapNotNull { it.toFolder() }.map { TargetFolder(it.id, it.name) }
        val targetPlaces = readServer(firestore.places(uid)).map {
            TargetPlace(it.id, it.getString(Fields.KAKAO_PLACE_ID), deleted = it.isTombstone())
        }
        val shortcodes = local.posts.mapNotNull { it.shortcode?.takeIf(String::isNotBlank) }.distinct()
        val urlsWithoutShortcode = local.posts.filter { it.shortcode.isNullOrBlank() }.map { it.instagramUrl }.distinct()
        val found = LinkedHashMap<String, DocumentSnapshot>()
        shortcodes.chunked(WHERE_IN_LIMIT).forEach { chunk ->
            readServer(firestore.posts(uid).whereIn(Fields.SHORTCODE, chunk)).forEach { found[it.id] = it }
        }
        urlsWithoutShortcode.chunked(WHERE_IN_LIMIT).forEach { chunk ->
            readServer(firestore.posts(uid).whereIn(Fields.INSTAGRAM_URL, chunk)).forEach { found[it.id] = it }
        }
        val targetPosts = found.values.mapNotNull { doc ->
            val url = doc.getString(Fields.INSTAGRAM_URL) ?: return@mapNotNull null
            TargetPost(doc.id, ShareKey.of(doc.getString(Fields.SHORTCODE), url), doc.placeIds().toSet())
        }
        return TargetSnapshot(targetFolders, targetPlaces, targetPosts)
    }

    /**
     * 계획을 서버에 쓴다(서버 확정까지 기다린다). 폴더 → 장소 → 장소 연결 → 게시물 순서이고 전부 멱등이라 끊기면 처음부터 다시 해도 된다.
     * 장소 연결(계정에 있던 장소의 postIds 보강)은 그 장소가 그사이 지워졌어도 합치기 전체를 막지 않도록 실패를 삼킨다.
     */
    suspend fun execute(uid: String, plan: MergePlan) {
        firestore.commitInChunksAndAwait("합치기 폴더", plan.folderWrites.map { f ->
            { batch -> batch.set(firestore.folders(uid).document(f.id), Folder(f.id, f.name, f.createdAt, f.color, f.iconKey).toFields()) }
        })
        firestore.commitInChunksAndAwait("합치기 장소", plan.placeWrites.map { p ->
            { batch -> batch.set(firestore.places(uid).document(p.id), p.toPlace().toFields()) }
        })
        try {
            firestore.commitInChunksAndAwait("합치기 장소 연결", plan.placeLinks.map { link ->
                { batch ->
                    batch.update(
                        firestore.places(uid).document(link.placeId),
                        placeUpdate(Fields.POST_IDS to FieldValue.arrayUnion(*link.postIds.toTypedArray())),
                    )
                }
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "합치기 장소 연결 일부 실패(${e.javaClass.simpleName}) — 건너뛴다")
        }
        firestore.commitInChunksAndAwait("합치기 게시물", plan.postWrites.map { p ->
            { batch -> batch.set(firestore.posts(uid).document(p.id), p.toPost().toFields(p.placeIds, p.candidates.map { it.toCandidate(p.id) })) }
        })
    }

    /** 버려진 익명 게시물의 썸네일을, 살아남는 계정 쪽 게시물에 썸네일이 없을 때만 그 postId 이름으로 옮긴다. */
    fun adoptThumbnails(adoptions: List<ThumbnailAdoption>) {
        adoptions.forEach { thumbnailStore.adopt(it.fromPostId, it.toPostId) }
    }

    private suspend fun readAll(query: Query): List<DocumentSnapshot> = try {
        withTimeout(READ_TIMEOUT_MILLIS) { query.get(Source.SERVER).await().documents }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        query.get(Source.CACHE).await().documents
    }

    private suspend fun readServer(query: Query): List<DocumentSnapshot> =
        withTimeout(READ_TIMEOUT_MILLIS) { query.get(Source.SERVER).await().documents }

    private companion object {
        const val TAG = "EonjeAccount"
        const val WHERE_IN_LIMIT = 30
        const val READ_TIMEOUT_MILLIS = 20_000L
    }
}

// ── 스냅샷 ↔ 도메인 모델 (문서 모양은 매퍼가 정한다) ──

private fun Place.toSnap() = PlaceSnap(
    id, name, address, latitude, longitude, kakaoPlaceId, category, status.name, createdAt, resolvedAt, folderId, postIds,
)

private fun PlaceSnap.toPlace() = Place(
    id = id, name = name, address = address, latitude = latitude, longitude = longitude, kakaoPlaceId = kakaoPlaceId,
    category = category, status = enumOrDefault(status, ResolveStatus.PENDING), createdAt = createdAt, resolvedAt = resolvedAt,
    folderId = folderId, postIds = postIds,
)

private fun SavedPost.toSnap(placeIds: List<String>, candidates: List<ExtractedCandidate>) = PostSnap(
    id = id, shortcode = shortcode, instagramUrl = instagramUrl, caption = caption, status = status.name,
    extractedCount = extractedCount, createdAt = createdAt, unresolvedReason = unresolvedReason?.name,
    placeIds = placeIds,
    candidates = candidates.map {
        CandidateSnap(it.extractionIndex, it.extractedName, it.rank, it.place.kakaoPlaceId, it.place.name, it.place.address,
            it.place.category, it.place.latitude, it.place.longitude)
    },
)

private fun PostSnap.toPost() = SavedPost(
    id = id, shortcode = shortcode, instagramUrl = instagramUrl, caption = caption, thumbnailUrl = null,
    status = enumOrDefault(status, ResolveStatus.PENDING), extractedCount = extractedCount, createdAt = createdAt,
    unresolvedReason = unresolvedReason?.let { name -> UnresolvedReason.entries.firstOrNull { it.name == name } },
)

private fun CandidateSnap.toCandidate(postId: String) = ExtractedCandidate(
    postId = postId, extractionIndex = extractionIndex, extractedName = extractedName, rank = rank,
    place = PlaceCandidate(kakaoPlaceId, name, address, category, latitude, longitude),
)

private inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
