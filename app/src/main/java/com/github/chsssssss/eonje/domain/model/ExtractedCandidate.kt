package com.github.chsssssss.eonje.domain.model

/**
 * 다중 장소 게시물(추출 2건 이상)에서 추출된 항목 하나에 대한 카카오 검색 후보 하나.
 * 항목([extractionIndex])마다 최대 5개가 [rank] 순서로 저장되고, 정리 화면 다중 모드에서
 * 사용자가 항목별로 확정할 후보를 고른다. 저장소(Room, 이후 Firestore)와 무관한 순수 모델이다.
 */
data class ExtractedCandidate(
    val postId: String,
    /** 게시물에서 추출된 항목의 순번(0부터). */
    val extractionIndex: Int,
    /** 캡션에서 추출한 상호명(검색에 쓴 이름). 후보의 실제 이름은 [place]에 있다. */
    val extractedName: String,
    /** 같은 항목 안에서의 후보 순위(0이 가장 유력). */
    val rank: Int,
    val place: PlaceCandidate,
)
