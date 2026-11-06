package com.github.chsssssss.eonje.domain.repository

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
}
