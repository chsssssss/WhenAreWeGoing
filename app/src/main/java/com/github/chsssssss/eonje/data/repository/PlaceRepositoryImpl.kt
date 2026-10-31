package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRef
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.domain.repository.PlaceRepository
import com.github.chsssssss.eonje.domain.repository.PlacesChangedNotifier
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PlaceRepositoryImpl @Inject constructor(
    private val placeDao: PlaceDao,
    private val crossRefDao: PostPlaceCrossRefDao,
    private val placesChangedNotifier: PlacesChangedNotifier,
) : PlaceRepository {

    override suspend fun findByKakaoPlaceId(kakaoPlaceId: String): PlaceEntity? =
        placeDao.findByKakaoPlaceId(kakaoPlaceId)

    override suspend fun findById(id: String): PlaceEntity? = placeDao.findById(id)

    override suspend fun save(place: PlaceEntity): String {
        val existing = place.kakaoPlaceId?.let { placeDao.findByKakaoPlaceId(it) }
        if (existing != null) return existing.id
        placeDao.insert(place)
        placesChangedNotifier.notifyPlacesChanged()
        return place.id
    }

    override suspend fun linkPostToPlace(postId: String, placeId: String) {
        crossRefDao.insert(PostPlaceCrossRef(postId = postId, placeId = placeId))
    }

    override suspend fun postIdsForPlace(placeId: String): List<String> =
        crossRefDao.postIdsForPlace(placeId)

    override fun observeAll(): Flow<List<PlaceEntity>> = placeDao.observeAll()

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
