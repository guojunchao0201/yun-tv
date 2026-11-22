package com.yuntv.data.cover

import com.yuntv.core.database.CoverCacheEntity
import com.yuntv.core.database.CoverFailureEntity
import com.yuntv.core.model.CoverImage
import com.yuntv.core.model.CoverRepository
import com.yuntv.core.model.CoverSourceType
import com.yuntv.core.model.DriveItem
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class DefaultCoverRepository(
    private val store: CoverStore,
    private val coversDirectory: File,
    private val httpClient: OkHttpClient,
    private val applicationScope: CoroutineScope,
    private val generator: CoverGenerator = AndroidCoverGenerator(),
    private val maxCacheBytes: Long = DEFAULT_MAX_CACHE_BYTES,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoverRepository {
    private val permits = Semaphore(MAX_CONCURRENT_JOBS)
    private val inFlight = ConcurrentHashMap<Long, Job>()

    override fun observeCover(fsId: Long): Flow<CoverImage?> = store.observe(fsId).map { entity ->
        entity?.takeIf { File(it.localPath).isFile }?.toModel()
    }

    override fun requestCover(item: DriveItem, videoUrl: String?) {
        if (item.isDirectory) return
        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            try {
                permits.withPermit { prepare(item, videoUrl) }
            } finally {
                inFlight.remove(item.fsId)
            }
        }
        val existing = inFlight.putIfAbsent(item.fsId, job)
        if (existing == null) {
            job.start()
        } else {
            job.cancel()
            if (videoUrl != null) existing.invokeOnCompletion { requestCover(item, videoUrl) }
        }
    }

    private suspend fun prepare(item: DriveItem, videoUrl: String?) = withContext(workDispatcher) {
        coversDirectory.mkdirs()
        val now = nowEpochSeconds()
        val cached = store.get(item.fsId)
        if (cached != null &&
            cached.sourceModifiedAtEpochSeconds == item.modifiedAtEpochSeconds &&
            File(cached.localPath).isFile
        ) {
            store.touch(item.fsId, now)
            return@withContext
        }
        cached?.let { removeCachedFile(it) }

        val recentFailure = store.getFailure(item.fsId)
        if (recentFailure != null &&
            recentFailure.sourceModifiedAtEpochSeconds == item.modifiedAtEpochSeconds &&
            now - recentFailure.failedAtEpochSeconds < FAILURE_RETRY_SECONDS
        ) return@withContext

        val destination = File(
            coversDirectory,
            "${item.fsId}_${item.modifiedAtEpochSeconds ?: 0}.webp",
        )
        var generated: GeneratedCover? = null
        var sourceType: CoverSourceType? = null
        item.thumbnailUrl?.let { thumbnailUrl ->
            runCatching {
                generator.fromImage(download(thumbnailUrl), destination)
            }.onSuccess {
                generated = it
                sourceType = CoverSourceType.REMOTE_THUMBNAIL
            }
        }
        if (generated == null && videoUrl != null) {
            runCatching {
                generator.fromVideo(videoUrl, CDN_HEADERS, destination)
            }.onSuccess {
                generated = it
                sourceType = CoverSourceType.VIDEO_FRAME
            }
        }

        val result = generated
        val source = sourceType
        if (result != null && source != null) {
            store.save(
                CoverCacheEntity(
                    fsId = item.fsId,
                    sourceModifiedAtEpochSeconds = item.modifiedAtEpochSeconds,
                    sourceType = source.name,
                    localPath = destination.absolutePath,
                    width = result.width,
                    height = result.height,
                    fileSizeBytes = result.fileSizeBytes,
                    createdAtEpochSeconds = now,
                    lastAccessedAtEpochSeconds = now,
                ),
            )
            store.deleteFailure(item.fsId)
            trimCache()
        } else if (videoUrl != null) {
            destination.delete()
            File(destination.parentFile, "${destination.name}.tmp").delete()
            store.saveFailure(CoverFailureEntity(item.fsId, item.modifiedAtEpochSeconds, now))
        }
    }

    private suspend fun download(url: String): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "缩略图下载失败：HTTP ${response.code}" }
            val body = checkNotNull(response.body) { "缩略图响应为空" }
            check(body.contentLength() <= MAX_DOWNLOAD_BYTES) { "缩略图过大" }
            body.byteStream().use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    check(total <= MAX_DOWNLOAD_BYTES) { "缩略图过大" }
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
        }
    }

    private suspend fun trimCache() {
        var total = store.totalFileSize()
        if (total <= maxCacheBytes) return
        val target = (maxCacheBytes * TRIM_TARGET_RATIO).toLong()
        for (entry in store.oldestFirst()) {
            if (total <= target) break
            removeCachedFile(entry)
            total -= entry.fileSizeBytes
        }
    }

    private suspend fun removeCachedFile(entity: CoverCacheEntity) {
        File(entity.localPath).delete()
        store.delete(entity.fsId)
    }

    private fun CoverCacheEntity.toModel() = CoverImage(
        fsId = fsId,
        localPath = localPath,
        width = width,
        height = height,
        sourceType = CoverSourceType.valueOf(sourceType),
    )

    private companion object {
        const val MAX_CONCURRENT_JOBS = 2
        const val FAILURE_RETRY_SECONDS = 24 * 60 * 60L
        const val DEFAULT_MAX_CACHE_BYTES = 512L * 1024L * 1024L
        const val MAX_DOWNLOAD_BYTES = 20 * 1024 * 1024L
        const val TRIM_TARGET_RATIO = 0.9
        const val USER_AGENT = "pan.baidu.com"
        val CDN_HEADERS = mapOf("User-Agent" to USER_AGENT)
    }
}
