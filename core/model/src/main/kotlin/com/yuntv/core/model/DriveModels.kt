package com.yuntv.core.model

import kotlinx.coroutines.flow.Flow

data class DriveItem(
    val fsId: Long,
    val parentPath: String,
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtEpochSeconds: Long?,
    val category: Int?,
    val mimeType: String?,
    val thumbnailUrl: String?,
)

data class DirectorySnapshot(
    val path: String,
    val items: List<DriveItem>,
    val fetchedAtEpochSeconds: Long,
    val source: SnapshotSource,
)

enum class SnapshotSource { CACHE, NETWORK }

interface CloudDriveRepository {
    fun observeDirectory(path: String): Flow<DirectorySnapshot?>
    suspend fun refreshDirectory(path: String, force: Boolean = false): DirectorySnapshot
}
