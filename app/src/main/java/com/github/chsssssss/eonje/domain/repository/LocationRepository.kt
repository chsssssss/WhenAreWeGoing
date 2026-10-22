package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.github.chsssssss.eonje.domain.model.LocationAvailability

interface LocationRepository {
    /** 권한이 없거나 위치를 못 잡으면 null. 홈 지도의 초기 중심을 정하는 데만 쓰여서 실패해도 무방하다. */
    suspend fun getCurrentLocation(): GeoPoint?

    /** 권한·기기 위치 설정 상태만 동기적으로 확인한다(측위는 하지 않음). */
    fun checkAvailability(): LocationAvailability
}
