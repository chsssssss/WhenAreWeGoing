package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.SavedPost
import kotlinx.coroutines.flow.Flow

interface PlaceRepository {
    suspend fun findByKakaoPlaceId(kakaoPlaceId: String): Place?
    suspend fun findById(id: String): Place?

    /** Upserts by kakaoPlaceId — an existing place is reused rather than duplicated. Returns the place's id. */
    suspend fun save(place: Place): String

    suspend fun linkPostToPlace(postId: String, placeId: String)
    suspend fun postIdsForPlace(placeId: String): List<String>
    fun observeAll(): Flow<List<Place>>

    /** 로컬 데이터에 있는 장소 전부. 서버를 읽지 않는다 — 위젯용(비어 있으면 빈 목록). */
    suspend fun loadCachedAll(): List<Place>

    /**
     * 한 장소가 나온 게시물(최신순). 홈 상세 모드가 열려 있는 동안에만 구독한다 — 목록 화면은 [Place.postIds]·[Place.thumbnailUrl]만 쓰고
     * 게시물 문서는 읽지 않는다(Firestore는 읽은 문서 수만큼 과금).
     */
    fun observePostsForPlace(placeId: String): Flow<List<SavedPost>>

    /** folderId가 null이면 미분류로 되돌린다. */
    suspend fun assignFolder(placeId: String, folderId: String?)
    suspend fun assignFolderToPlaces(placeIds: List<String>, folderId: String?)

    /** 장소를 지우고, 게시물과의 연결 정보도 함께 지운다. 게시물 자체는 남는다. */
    suspend fun delete(placeId: String)
}
