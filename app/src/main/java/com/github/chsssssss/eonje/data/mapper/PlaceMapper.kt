package com.github.chsssssss.eonje.data.mapper

import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.domain.model.Place

/** 저장소의 memo 컬럼은 도메인으로 올리지 않는다(메모 기능이 제거됨). */
fun PlaceEntity.toDomain() = Place(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    kakaoPlaceId = kakaoPlaceId,
    category = category,
    status = status,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    folderId = folderId,
)

/**
 * 도메인 [Place]에는 memo가 없어서 기본값은 null이다. 이미 있는 행을 같은 id로 덮어쓰는 경우엔
 * 호출한 쪽(저장소)이 기존 행의 memo를 읽어 [memo]로 넘겨서 값이 null로 지워지지 않게 한다.
 */
fun Place.toEntity(memo: String? = null) = PlaceEntity(
    id = id,
    name = name,
    address = address,
    latitude = latitude,
    longitude = longitude,
    kakaoPlaceId = kakaoPlaceId,
    category = category,
    memo = memo,
    status = status,
    createdAt = createdAt,
    resolvedAt = resolvedAt,
    folderId = folderId,
)
