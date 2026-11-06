package com.github.chsssssss.eonje.domain.repository

/** 공유받은 게시물의 후속 작업을 WorkManager에 맡긴다. */
interface PostWorkScheduler {
    /** 캡션 조회·파싱·검색(F2)을 예약한다. 같은 게시물에 대해 이미 예약돼 있으면 중복으로 만들지 않는다. */
    fun scheduleCaptionParsing(postId: String)

    /**
     * 아직 로그인 전이라 바로 저장하지 못한 공유를 보관한다. WorkManager가 자기 DB에 즉시 저장하므로
     * 프로세스가 죽어도 URL이 남는다. 같은 [shareKey]는 한 번만 예약된다.
     */
    fun scheduleSaveSharedPost(shareKey: String, url: String, shortcode: String?, createdAt: Long)
}
