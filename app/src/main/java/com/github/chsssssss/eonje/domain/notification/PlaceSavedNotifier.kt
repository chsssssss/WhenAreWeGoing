package com.github.chsssssss.eonje.domain.notification

interface PlaceSavedNotifier {
    /** RESOLVED 확정 시 "○○식당 저장됨" 알림. 권한이 없으면 조용히 무시한다 — 알림은 최적화이지 필수 기능이 아니다. */
    fun notifyResolved(placeName: String)
}
