package com.github.chsssssss.eonje.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** 홈 화면이 보여줄 이전 상태. */
enum class LegacyMigrationPhase {
    /** 이전할 게 없거나(신규 설치, Room이 비어 있음) 이 uid로 이미 끝났다. 화면엔 아무 영향이 없다. */
    NOT_NEEDED,

    /** 이전 대상이 있고 지금 Worker가 옮기는 중이다. */
    MIGRATING,

    /** 이전 대상이 있는데 아직 시작하지 못했다 — 오프라인, 로그인 대기, 재시도 대기. */
    WAITING,
}

/**
 * 업데이트 전의 Room 데이터를 Firestore로 옮기는 일(1회)의 진행 상태. 옮기는 동안 Firestore엔 일부만 있어서,
 * "게시물이 없다"를 곧바로 "삭제됐다"로 판단하면 안 되는 곳에서 이걸 확인한다.
 */
interface LegacyMigrationStatus {
    /** 현재 로그인한 uid에 대해 이전이 끝났는지(또는 옮길 게 없다고 확인됐는지). 로그인 전이면 false. */
    suspend fun isComplete(): Boolean

    /**
     * 이전이 끝나기 전에만 의미가 있다: 같은 shortcode의 게시물이 아직 Firestore로 못 옮겨진 Room에 있는지.
     * 이전이 끝났으면 항상 false다(Firestore만 보면 된다).
     */
    suspend fun isLegacyDuplicate(shortcode: String): Boolean

    /**
     * 화면용 이전 상태. 새 서버 호출 없이 로컬 정보(DataStore의 uid 기반 완료 값, Room 데이터 유무, Worker 상태)로만 계산한다.
     * 기본값은 "이전 없음"이라 이 상태를 모르는 구현(테스트 대역 등)엔 영향이 없다.
     */
    /**
     * 이전에서 건너뛴(Firestore에 못 올린) 게시물 ID. Room에는 그대로 남아 있다. 이 게시물의 썸네일 파일은 서버에 문서가 없어도
     * 고아가 아니다(지우면 복구할 수 없다).
     */
    suspend fun skippedPostIds(): Set<String> = emptySet()

    fun observePhase(): Flow<LegacyMigrationPhase> = flowOf(LegacyMigrationPhase.NOT_NEEDED)
}
