package com.github.chsssssss.eonje.domain.repository

/**
 * 장소 목록(저장·삭제·폴더 배정)이 바뀌었음을 알린다. 지금은 홈 화면 위젯 갱신용.
 * 저장 흐름을 막지 않도록 구현체는 즉시 반환하고, 실패해도 예외를 던지지 않는다.
 */
fun interface PlacesChangedNotifier {
    fun notifyPlacesChanged()
}
