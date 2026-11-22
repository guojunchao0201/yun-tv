package com.yuntv.data.cover

import com.yuntv.core.database.CoverCacheEntity
import com.yuntv.core.database.CoverDao
import com.yuntv.core.database.CoverFailureEntity
import kotlinx.coroutines.flow.Flow

interface CoverStore {
    fun observe(fsId: Long): Flow<CoverCacheEntity?>
    suspend fun get(fsId: Long): CoverCacheEntity?
    suspend fun save(entity: CoverCacheEntity)
    suspend fun touch(fsId: Long, accessedAt: Long)
    suspend fun delete(fsId: Long)
    suspend fun totalFileSize(): Long
    suspend fun oldestFirst(): List<CoverCacheEntity>
    suspend fun getFailure(fsId: Long): CoverFailureEntity?
    suspend fun saveFailure(entity: CoverFailureEntity)
    suspend fun deleteFailure(fsId: Long)
}

class RoomCoverStore(private val dao: CoverDao) : CoverStore {
    override fun observe(fsId: Long) = dao.observeCover(fsId)
    override suspend fun get(fsId: Long) = dao.getCover(fsId)
    override suspend fun save(entity: CoverCacheEntity) = dao.upsertCover(entity)
    override suspend fun touch(fsId: Long, accessedAt: Long) = dao.touch(fsId, accessedAt)
    override suspend fun delete(fsId: Long) = dao.deleteCover(fsId)
    override suspend fun totalFileSize() = dao.totalFileSize()
    override suspend fun oldestFirst() = dao.oldestFirst()
    override suspend fun getFailure(fsId: Long) = dao.getFailure(fsId)
    override suspend fun saveFailure(entity: CoverFailureEntity) = dao.upsertFailure(entity)
    override suspend fun deleteFailure(fsId: Long) = dao.deleteFailure(fsId)
}
