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
    suspend fun findItem(fsId: Long): DriveItem?
    suspend fun resolvePlayback(fsIds: List<Long>): Map<Long, PlaybackSource>
    suspend fun invalidatePlayback(fsId: Long)
}

data class PlaybackSource(
    val fsId: Long,
    val url: String,
    val obtainedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
    val requiredHeaders: Map<String, String>,
)

data class PlaybackProgress(
    val fsId: Long,
    val path: String,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val updatedAtEpochSeconds: Long,
)

interface PlaybackProgressRepository {
    fun observeProgress(fsId: Long): Flow<PlaybackProgress?>
    suspend fun saveProgress(progress: PlaybackProgress)
}

data class CoverImage(
    val fsId: Long,
    val localPath: String,
    val width: Int,
    val height: Int,
    val sourceType: CoverSourceType,
)

enum class CoverSourceType { REMOTE_THUMBNAIL, VIDEO_FRAME }

interface CoverRepository {
    fun observeCover(fsId: Long): Flow<CoverImage?>

    /**
     * Starts an application-scoped, de-duplicated cover preparation job.
     * [videoUrl] is optional until the dlink stage is connected.
     */
    fun requestCover(item: DriveItem, videoUrl: String? = null)
}
