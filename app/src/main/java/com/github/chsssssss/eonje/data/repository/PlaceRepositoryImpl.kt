package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRef
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.data.mapper.toDomain
import com.github.chsssssss.eonje.data.mapper.toEntity
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val crossRefDao: PostPlaceCrossRefDao,
    private val placesChangedNotifier: PlacesChangedNotifier,
) : PlaceRepository {

    override suspend fun findByKakaoPlaceId(kakaoPlaceId: String): Place? =
        placeDao.findByKakaoPlaceId(kakaoPlaceId)?.toDomain()

    override suspend fun findById(id: String): Place? = placeDao.findById(id)?.toDomain()

    override suspend fun save(place: Place): String {
        val existing = place.kakaoPlaceId?.let { placeDao.findByKakaoPlaceId(it) }
        if (existing != null) return existing.id
        // 도메인 Place에는 memo가 없다. DAO의 insert는 같은 id가 있으면 행을 통째로 교체(REPLACE)하므로,
        // 기존 행의 memo를 읽어서 그대로 이어 써야 이 저장이 memo를 null로 지우지 않는다.
        val preservedMemo = placeDao.findById(place.id)?.memo
        placeDao.insert(place.toEntity(memo = preservedMemo))
        placesChangedNotifier.notifyPlacesChanged()
        return place.id
    }

    override suspend fun linkPostToPlace(postId: String, placeId: String) {
        crossRefDao.insert(PostPlaceCrossRef(postId = postId, placeId = placeId))
    }

    override suspend fun postIdsForPlace(placeId: String): List<String> =
        crossRefDao.postIdsForPlace(placeId)

    override fun observeAll(): Flow<List<Place>> = placeDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun assignFolder(placeId: String, folderId: String?) {
        placeDao.updateFolder(placeId, folderId)
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun assignFolderToPlaces(placeIds: List<String>, folderId: String?) {
        placeDao.updateFolderForPlaces(placeIds, folderId)
        placesChangedNotifier.notifyPlacesChanged()
    }

    override suspend fun delete(placeId: String) {
        crossRefDao.deleteForPlace(placeId)
        placeDao.delete(placeId)
        placesChangedNotifier.notifyPlacesChanged()
    }
}
