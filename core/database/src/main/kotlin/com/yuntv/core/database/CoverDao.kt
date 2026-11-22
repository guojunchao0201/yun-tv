package com.yuntv.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CoverDao {
    @Query("SELECT * FROM cover_cache WHERE fs_id = :fsId")
    fun observeCover(fsId: Long): Flow<CoverCacheEntity?>

    @Query("SELECT * FROM cover_cache WHERE fs_id = :fsId")
    suspend fun getCover(fsId: Long): CoverCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCover(cover: CoverCacheEntity)

    @Query("UPDATE cover_cache SET last_accessed_at = :accessedAt WHERE fs_id = :fsId")
    suspend fun touch(fsId: Long, accessedAt: Long)

    @Query("DELETE FROM cover_cache WHERE fs_id = :fsId")
    suspend fun deleteCover(fsId: Long)

    @Query("SELECT COALESCE(SUM(file_size), 0) FROM cover_cache")
    suspend fun totalFileSize(): Long

    @Query("SELECT * FROM cover_cache ORDER BY last_accessed_at ASC")
    suspend fun oldestFirst(): List<CoverCacheEntity>

    @Query("SELECT * FROM cover_failure WHERE fs_id = :fsId")
    suspend fun getFailure(fsId: Long): CoverFailureEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFailure(failure: CoverFailureEntity)

    @Query("DELETE FROM cover_failure WHERE fs_id = :fsId")
    suspend fun deleteFailure(fsId: Long)
}
