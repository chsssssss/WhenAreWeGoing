package com.github.chsssssss.eonje.domain.model

/**
 * UNRESOLVED 상태가 된 구체적인 원인. 인박스에서 "계정을 등록해야 하는지"와
 * "계정은 맞는데 장소를 못 찾은 건지"를 구분해서 보여주는 데 쓴다.
 */
enum class UnresolvedReason {
    /** shortcode가 등록된 계정 어디에서도 발견되지 않음 — 계정 등록(또는 재동기화)이 필요할 가능성이 높다. */
    ACCOUNT_NOT_FOUND,

    /** 계정/게시물은 확인됐지만 캡션 파싱이나 카카오 검색에서 장소를 찾지 못함. */
    PLACE_NOT_FOUND,

    /** 네트워크 문제로 재시도를 반복하다 한도를 넘겨 포기함. */
    NETWORK_ERROR,
}
