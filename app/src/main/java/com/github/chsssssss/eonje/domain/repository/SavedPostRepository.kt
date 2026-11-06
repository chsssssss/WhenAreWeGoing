package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import kotlinx.coroutines.flow.Flow

interface SavedPostRepository {
    suspend fun findByShortcode(shortcode: String): SavedPost?
    suspend fun findById(id: String): SavedPost?
    suspend fun findByIds(ids: List<String>): List<SavedPost>
    suspend fun save(post: SavedPost)
    suspend fun updateStatus(id: String, status: ResolveStatus, unresolvedReason: UnresolvedReason?)
    suspend fun updateExtraction(id: String, caption: String?, thumbnailUrl: String?, extractedCount: Int)
    suspend fun deleteByIds(ids: List<String>)

    /** 이 게시물의 로컬 썸네일(file://) 경로를 비운다. 원격 URL이 저장돼 있으면 그대로 둔다. */
    suspend fun clearLocalThumbnail(id: String)
    fun observeAll(): Flow<List<SavedPost>>

    /** 이 기기의 로컬 데이터에 있는 게시물 ID. 서버를 읽지 않는다(썸네일 고아 파일 판정용). */
    suspend fun cachedPostIds(): Set<String>

    /**
     * [ids] 중 **서버에 없다고 확인된** id. 서버가 대답한 것만 판단한다 — 오프라인이거나 조회가 실패하거나 로그인 전이면
     * 그 id는 "없다고 확인된" 게 아니므로 결과에 넣지 않는다(호출한 쪽은 지우지 말고 다음 기회로 미뤄야 한다).
     * 로컬 캐시에 없는 게시물이 서버엔 있는지 확인하려고 쓴다(썸네일 고아 판정). **서버 읽기가 든다** — 질의당 최소 1건,
     * 서버에 있는 문서만큼 더 든다.
     */
    suspend fun findIdsAbsentOnServer(ids: Collection<String>): Set<String>

    /** 로컬 데이터에 있는 미확정 게시물. 서버를 읽지 않는다 — 백그라운드 알림 워커용(비어 있으면 빈 목록). */
    suspend fun loadCachedUnresolved(): List<SavedPost>
    fun observeUnresolved(): Flow<List<SavedPost>>
}
