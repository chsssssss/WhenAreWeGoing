package com.github.chsssssss.eonje.data.analytics

/**
 * Firebase Analytics 이벤트·파라미터 이름 모음. 호출부에서 FirebaseAnalytics를 직접 부른다.
 * 파라미터에 URL·캡션·가게 이름 같은 게시물 내용은 절대 넣지 않는다 — 개수·상태·소요 시간만 남긴다.
 */
object Events {
    /** F1: 공유받은 게시물을 새로 저장한 직후 (중복 공유·URL 없음은 제외). */
    const val SHARE_RECEIVED = "share_received"

    /** F2: fetchInstagramMeta(Apify) 호출 직후. [Param.STATUS] = [Status], [Param.LATENCY_MS]. */
    const val APIFY_FETCH = "apify_fetch"

    /** F2: 캡션 파싱 한 번이 결과를 낸 직후. [Param.ENGINE], [Param.STAGE], [Param.EXTRACTED_COUNT]. */
    const val CAPTION_PARSE = "caption_parse"

    /** 게시물이 RESOLVED로 확정됨. [Param.SOURCE] = [Source]. */
    const val POST_RESOLVED = "post_resolved"

    /** 정리 화면 다중 모드에서 저장. [Param.EXTRACTED_COUNT], [Param.SAVED_COUNT]. */
    const val MULTI_PLACE_SAVED = "multi_place_saved"

    object Param {
        const val STATUS = "status"
        const val LATENCY_MS = "latency_ms"
        const val ENGINE = "engine"
        const val STAGE = "stage"
        const val EXTRACTED_COUNT = "extracted_count"
        const val SOURCE = "source"
        const val SAVED_COUNT = "saved_count"
    }

    object Status {
        const val SUCCESS = "success"
        /** 호출은 성공했지만 게시물을 못 받음 (비공개·삭제된 게시물 등). */
        const val EMPTY = "empty"
        const val ERROR = "error"
    }

    object Engine {
        const val NANO = "nano"
        const val CLOUD = "cloud"
    }

    object Stage {
        const val CAPTION = "caption"
        const val IMAGE = "image"
    }

    object Source {
        const val AUTO = "auto"
        const val MANUAL = "manual"
    }
}
