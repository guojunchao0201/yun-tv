package com.yuntv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackDao {
    @Query("SELECT * FROM dlink_cache WHERE fs_id IN (:fsIds)")
    suspend fun getDlinks(fsIds: List<Long>): List<DlinkCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDlinks(entries: List<DlinkCacheEntity>)

    @Query("DELETE FROM dlink_cache WHERE fs_id = :fsId")
    suspend fun deleteDlink(fsId: Long)

    @Query("SELECT * FROM playback_progress WHERE fs_id = :fsId")
    fun observeProgress(fsId: Long): Flow<PlaybackProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: PlaybackProgressEntity)
}
