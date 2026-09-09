package com.github.chsssssss.eonje.data.repository

import com.github.chsssssss.eonje.data.local.WatchedAccountDao
import com.github.chsssssss.eonje.data.local.WatchedAccountEntity
import com.github.chsssssss.eonje.domain.repository.WatchedAccountRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class WatchedAccountRepositoryImpl @Inject constructor(
    private val dao: WatchedAccountDao,
) : WatchedAccountRepository {
    override fun observeAll(): Flow<List<WatchedAccountEntity>> = dao.observeAll()

    override suspend fun getAll(): List<WatchedAccountEntity> = dao.getAll()

    override suspend fun findByUsername(username: String): WatchedAccountEntity? =
        dao.findByUsername(username)

    override suspend fun save(account: WatchedAccountEntity) = dao.insert(account)

    override suspend fun delete(username: String) = dao.delete(username)

    override suspend fun markSynced(username: String) =
        dao.markSynced(username, System.currentTimeMillis())

    override suspend fun markSyncFailed(username: String, error: String) =
        dao.markSyncFailed(username, error)
}
