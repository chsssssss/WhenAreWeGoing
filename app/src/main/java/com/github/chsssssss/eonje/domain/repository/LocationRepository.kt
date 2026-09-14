package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.GeoPoint

interface LocationRepository {
    /** 권한이 없거나 위치를 못 잡으면 null. 홈 지도의 초기 중심을 정하는 데만 쓰여서 실패해도 무방하다. */
    suspend fun getCurrentLocation(): GeoPoint?
}
