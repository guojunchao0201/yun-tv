package com.yuntv.data.baidu

import com.yuntv.core.database.DlinkCacheEntity
import com.yuntv.core.database.PlaybackDao

interface PlaybackCacheStore {
    suspend fun get(fsIds: List<Long>): List<DlinkCacheEntity>
    suspend fun save(entries: List<DlinkCacheEntity>)
    suspend fun delete(fsId: Long)
}

class RoomPlaybackCacheStore(
    private val dao: PlaybackDao,
) : PlaybackCacheStore {
    override suspend fun get(fsIds: List<Long>): List<DlinkCacheEntity> =
        fsIds.chunked(SQLITE_IN_BATCH_SIZE).flatMap { dao.getDlinks(it) }

    override suspend fun save(entries: List<DlinkCacheEntity>) = dao.upsertDlinks(entries)
    override suspend fun delete(fsId: Long) = dao.deleteDlink(fsId)

    private companion object {
        const val SQLITE_IN_BATCH_SIZE = 900
    }
}
