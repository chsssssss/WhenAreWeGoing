package com.github.chsssssss.eonje.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(folder: FolderEntity)

    @Query("SELECT * FROM folders WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): FolderEntity?

    @Query("SELECT * FROM folders ORDER BY name ASC")
    fun observeAll(): Flow<List<FolderEntity>>

    @Query("UPDATE folders SET color = :color, iconKey = :iconKey WHERE id = :id")
    suspend fun updateAppearance(id: String, color: Int, iconKey: String)

    @Query("DELETE FROM folders WHERE id = :id")
    suspend fun delete(id: String)

    // ── Room → Firestore 이전(읽기 전용). 이전 중엔 Room이 바뀌지 않으므로 id 커서로 이어서 읽을 수 있다. ──

    @Query("SELECT * FROM folders WHERE id > :afterId ORDER BY id ASC LIMIT :limit")
    suspend fun pageAfter(afterId: String, limit: Int): List<FolderEntity>

    @Query("SELECT COUNT(*) FROM folders")
    suspend fun count(): Int
}
