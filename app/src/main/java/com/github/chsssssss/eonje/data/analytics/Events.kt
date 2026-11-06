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

    /** 온보딩 화면이 뜸. [Param.SOURCE] = [OnboardingEntry]. */
    const val ONBOARDING_VIEWED = "onboarding_viewed"

    /** 온보딩 마지막 장에서 "시작하기". [Param.SOURCE] = [OnboardingEntry], [Param.STEP] = 본 장 수. */
    const val ONBOARDING_COMPLETED = "onboarding_completed"

    /** 온보딩을 "건너뛰기"로 끝냄. [Param.SOURCE] = [OnboardingEntry], [Param.STEP] = 건너뛴 장(1부터). */
    const val ONBOARDING_SKIPPED = "onboarding_skipped"

    /** 온보딩을 끝낸 뒤 처음으로 공유 저장에 성공함 — 기기당 한 번만. */
    const val FIRST_SHARE_AFTER_ONBOARDING = "first_share_after_onboarding"

    /** '오늘의 맛집' 위젯을 처음 하나 놓음 (AppWidgetProvider.onEnabled — 두 번째 위젯부터는 안 찍힘). */
    const val WIDGET_ADDED = "widget_added"

    /** 홈 화면의 '오늘의 맛집' 위젯이 전부 제거됨 (AppWidgetProvider.onDisabled). */
    const val WIDGET_REMOVED = "widget_removed"

    /** 위젯을 탭해서 앱이 열림. [Param.TARGET] = [WidgetTarget]. 가게 이름·id는 넣지 않는다. */
    const val WIDGET_CLICKED = "widget_clicked"

    /**
     * 업데이트 후 기존 썸네일 1회 정리가 끝남. [Param.FILE_COUNT] = 줄이거나 지운 파일 수,
     * [Param.SAVED_BYTES] = 줄어든 총 바이트. 숫자만 남긴다.
     */
    const val THUMBNAIL_COMPACTION_DONE = "thumbnail_compaction_done"

    /** 업데이트 후 Room → Firestore 이전을 이 uid로 새로 시작함(이어하기에선 안 찍힘). 숫자·종류만 남긴다. */
    const val ROOM_MIGRATION_STARTED = "room_migration_started"

    /** 이전이 끝남. [Param.FOLDER_COUNT], [Param.PLACE_COUNT], [Param.POST_COUNT], [Param.SKIPPED_COUNT](규칙 거부 등으로 건너뛴 문서 수), [Param.SANITIZED_COUNT](규칙 상한에 맞게 고쳐서 옮긴 문서 수), [Param.DURATION_MS](처음 시작부터의 벽시계 시간). */
    const val ROOM_MIGRATION_COMPLETED = "room_migration_completed"

    /** 이전 한 번의 시도가 실패해서 다시 시도하게 됨. [Param.ERROR_TYPE] = [MigrationError]. 오류 메시지·내용은 넣지 않는다. */
    const val ROOM_MIGRATION_FAILED = "room_migration_failed"

    object Param {
        const val FOLDER_COUNT = "folder_count"
        const val PLACE_COUNT = "place_count"
        const val POST_COUNT = "post_count"
        const val SKIPPED_COUNT = "skipped_count"
        const val SANITIZED_COUNT = "sanitized_count"
        const val DURATION_MS = "duration_ms"
        const val ERROR_TYPE = "error_type"
        const val FILE_COUNT = "file_count"
        const val SAVED_BYTES = "saved_bytes"
        const val STATUS = "status"
        const val LATENCY_MS = "latency_ms"
        const val ENGINE = "engine"
        const val STAGE = "stage"
        const val EXTRACTED_COUNT = "extracted_count"
        const val SOURCE = "source"
        const val SAVED_COUNT = "saved_count"
        const val STEP = "step"
        const val TARGET = "target"
    }

    /** 위젯에서 무엇을 탭했는지. */
    object WidgetTarget {
        const val PLACE = "place"
        const val EMPTY = "empty"
    }

    /** 이전 실패 원인의 종류(예외 이름·메시지는 남기지 않는다). */
    object MigrationError {
        const val NETWORK = "network"
        const val PERMISSION = "permission"
        const val UNKNOWN = "unknown"
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

    /** 온보딩 진입 경로. */
    object OnboardingEntry {
        const val FIRST_RUN = "first_run"
        const val HOME_EMPTY = "home_empty"
        const val SETTINGS = "settings"
    }
}
