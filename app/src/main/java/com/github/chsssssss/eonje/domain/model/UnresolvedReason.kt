package com.github.chsssssss.eonje.domain.model

/** UNRESOLVED 상태가 된 구체적인 원인. */
enum class UnresolvedReason {
    /** 캡션 파싱이나 카카오 검색에서 장소를 찾지 못함. */
    PLACE_NOT_FOUND,

    /** 네트워크 문제로 재시도를 반복하다 한도를 넘겨 포기함. */
    NETWORK_ERROR,
}
