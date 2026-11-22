package com.yuntv.data.baidu

import com.yuntv.core.database.DirectoryDao
import com.yuntv.core.database.DirectorySnapshotEntity
import com.yuntv.core.database.DriveItemEntity
import com.yuntv.core.model.AuthRepository
import com.yuntv.core.model.CloudDriveRepository
import com.yuntv.core.model.DirectorySnapshot
import com.yuntv.core.model.DriveItem
import com.yuntv.core.model.MediaPathPolicy
import com.yuntv.core.model.SnapshotSource
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class DefaultCloudDriveRepository(
    private val client: BaiduFileClient,
    private val authRepository: AuthRepository,
    private val directoryDao: DirectoryDao,
    private val rootPath: String = MediaPathPolicy.DEFAULT_ROOT_PATH,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : CloudDriveRepository {
    private val inFlightRefreshes = ConcurrentHashMap<String, Deferred<DirectorySnapshot>>()

    override fun observeDirectory(path: String): Flow<DirectorySnapshot?> {
        val normalized = MediaPathPolicy.normalize(path)
        return combine(
            directoryDao.observeSnapshot(normalized),
            directoryDao.observeItems(normalized),
        ) { snapshot, items ->
            snapshot?.let {
                DirectorySnapshot(
                    path = it.path,
                    items = items.map { it.toModel() },
                    fetchedAtEpochSeconds = it.fetchedAtEpochSeconds,
                    source = SnapshotSource.CACHE,
                )
            }
        }
    }

    override suspend fun refreshDirectory(path: String, force: Boolean): DirectorySnapshot {
        val normalized = MediaPathPolicy.normalize(path)
        val mine = CompletableDeferred<DirectorySnapshot>()
        val existing = inFlightRefreshes.putIfAbsent(normalized, mine)
        if (existing != null) return existing.await()
        try {
            val result = refreshNow(normalized, force)
            mine.complete(result)
            return result
        } catch (error: Throwable) {
            mine.completeExceptionally(error)
            throw error
        } finally {
            inFlightRefreshes.remove(normalized, mine)
        }
    }

    private suspend fun refreshNow(path: String, force: Boolean): DirectorySnapshot {
        if (!force) cached(path)?.let { return it }

        val accessToken = authRepository.ensureValidAccessToken()
        val fetched = client.listDirectory(path, accessToken)
            .filter { item ->
                if (MediaPathPolicy.isRoot(path, rootPath)) item.isDirectory
                else item.isDirectory || item.isVideo
            }
            .sortedWith(compareByDescending<DriveItem> { it.isDirectory }.thenBy { naturalSortKey(it.name) })
        val fetchedAt = nowEpochSeconds()
        directoryDao.replaceDirectory(
            snapshot = DirectorySnapshotEntity(path, fetchedAt, fetched.size),
            items = fetched.map { it.toEntity() },
        )
        return DirectorySnapshot(path, fetched, fetchedAt, SnapshotSource.NETWORK)
    }

    private suspend fun cached(path: String): DirectorySnapshot? {
        val snapshot = directoryDao.getSnapshot(path) ?: return null
        return DirectorySnapshot(
            path = path,
            items = directoryDao.getItems(path).map { it.toModel() },
            fetchedAtEpochSeconds = snapshot.fetchedAtEpochSeconds,
            source = SnapshotSource.CACHE,
        )
    }

    private val DriveItem.isVideo: Boolean
        get() = category == VIDEO_CATEGORY || mimeType?.startsWith("video", ignoreCase = true) == true

    private fun DriveItem.toEntity() = DriveItemEntity(
        fsId = fsId,
        parentPath = parentPath,
        path = path,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        modifiedAtEpochSeconds = modifiedAtEpochSeconds,
        category = category,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl,
        sortKey = naturalSortKey(name),
    )

    private fun DriveItemEntity.toModel() = DriveItem(
        fsId = fsId,
        parentPath = parentPath,
        path = path,
        name = name,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        modifiedAtEpochSeconds = modifiedAtEpochSeconds,
        category = category,
        mimeType = mimeType,
        thumbnailUrl = thumbnailUrl,
    )

    private fun naturalSortKey(value: String): String = NUMBER_REGEX.replace(
        value.lowercase(Locale.ROOT),
    ) { match -> match.value.padStart(NUMBER_PADDING, '0') }

    private companion object {
        const val VIDEO_CATEGORY = 1
        const val NUMBER_PADDING = 20
        val NUMBER_REGEX = Regex("\\d+")
    }
}
