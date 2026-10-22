package com.github.chsssssss.eonje.domain.model

/** 홈 지도에서 현재 위치를 쓸 수 있는지 — 못 쓰면 이유에 따라 다른 설정 화면으로 안내한다. */
enum class LocationAvailability {
    AVAILABLE,
    PERMISSION_DENIED,  // 앱 위치 권한 없음 → 앱 정보(권한) 설정
    LOCATION_OFF,       // 권한은 있지만 기기 위치가 꺼짐 → 기기 위치 설정
}
