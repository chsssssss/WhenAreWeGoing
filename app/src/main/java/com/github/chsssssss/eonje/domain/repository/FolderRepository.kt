package com.github.chsssssss.eonje.domain.repository

import com.github.chsssssss.eonje.domain.model.Folder
import kotlinx.coroutines.flow.Flow

interface FolderRepository {
    fun observeAll(): Flow<List<Folder>>

    /** 로컬 데이터에 있는 폴더 전부. 서버를 읽지 않는다 — 위젯용(비어 있으면 빈 목록). */
    suspend fun loadCachedAll(): List<Folder>

    /** Reuses an existing folder by name rather than creating a duplicate. */
    suspend fun findOrCreateByName(name: String): Folder

    /** 마커 색상·아이콘을 지정해서 폴더를 만든다. 같은 이름이 이미 있으면 그 폴더의 색상·아이콘만 갱신한다. */
    suspend fun createWithAppearance(name: String, color: Int, iconKey: String): Folder

    /** 폴더를 지우고, 그 폴더에 있던 장소는 전부 미분류로 되돌린다. */
    suspend fun delete(folderId: String)
}
