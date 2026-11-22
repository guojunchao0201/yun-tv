package com.yuntv.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "cover_cache", primaryKeys = ["fs_id"])
data class CoverCacheEntity(
    @ColumnInfo(name = "fs_id") val fsId: Long,
    @ColumnInfo(name = "source_modified_at") val sourceModifiedAtEpochSeconds: Long?,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "local_path") val localPath: String,
    val width: Int,
    val height: Int,
    @ColumnInfo(name = "file_size") val fileSizeBytes: Long,
    @ColumnInfo(name = "created_at") val createdAtEpochSeconds: Long,
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAtEpochSeconds: Long,
)

@Entity(tableName = "cover_failure", primaryKeys = ["fs_id"])
data class CoverFailureEntity(
    @ColumnInfo(name = "fs_id") val fsId: Long,
    @ColumnInfo(name = "source_modified_at") val sourceModifiedAtEpochSeconds: Long?,
    @ColumnInfo(name = "failed_at") val failedAtEpochSeconds: Long,
)
