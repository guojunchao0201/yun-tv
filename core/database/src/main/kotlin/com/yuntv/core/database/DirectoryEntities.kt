package com.yuntv.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "directory_snapshot", primaryKeys = ["path"])
data class DirectorySnapshotEntity(
    val path: String,
    @ColumnInfo(name = "fetched_at") val fetchedAtEpochSeconds: Long,
    @ColumnInfo(name = "item_count") val itemCount: Int,
)

@Entity(
    tableName = "drive_item",
    primaryKeys = ["fs_id", "parent_path"],
    indices = [Index("parent_path"), Index(value = ["path"], unique = true)],
)
data class DriveItemEntity(
    @ColumnInfo(name = "fs_id") val fsId: Long,
    @ColumnInfo(name = "parent_path") val parentPath: String,
    val path: String,
    val name: String,
    @ColumnInfo(name = "is_directory") val isDirectory: Boolean,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "modified_at") val modifiedAtEpochSeconds: Long?,
    val category: Int?,
    @ColumnInfo(name = "mime_type") val mimeType: String?,
    @ColumnInfo(name = "thumbnail_url") val thumbnailUrl: String?,
    @ColumnInfo(name = "sort_key") val sortKey: String,
)
