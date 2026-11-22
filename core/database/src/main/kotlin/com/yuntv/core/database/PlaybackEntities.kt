package com.yuntv.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(tableName = "dlink_cache", primaryKeys = ["fs_id"])
data class DlinkCacheEntity(
    @ColumnInfo(name = "fs_id") val fsId: Long,
    val url: String,
    @ColumnInfo(name = "obtained_at") val obtainedAtEpochSeconds: Long,
    @ColumnInfo(name = "expires_at") val expiresAtEpochSeconds: Long,
)

@Entity(tableName = "playback_progress", primaryKeys = ["fs_id"])
data class PlaybackProgressEntity(
    @ColumnInfo(name = "fs_id") val fsId: Long,
    val path: String,
    @ColumnInfo(name = "position_ms") val positionMs: Long,
    @ColumnInfo(name = "duration_ms") val durationMs: Long,
    val completed: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAtEpochSeconds: Long,
)
