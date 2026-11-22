package com.yuntv.data.baidu

import com.yuntv.core.database.DlinkCacheEntity
import com.yuntv.core.model.AuthRepository
import com.yuntv.core.model.PlaybackSource
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl

class BaiduPlaybackResolver(
    private val client: BaiduFileService,
    private val authRepository: AuthRepository,
    private val cacheStore: PlaybackCacheStore,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    private val resolveMutex = Mutex()

    suspend fun resolve(fsIds: List<Long>): Map<Long, PlaybackSource> = resolveMutex.withLock {
        val requested = fsIds.distinct()
        if (requested.isEmpty()) return emptyMap()
        val now = nowEpochSeconds()
        val cached = cacheStore.get(requested).associateBy { it.fsId }
        val missing = requested.filter { fsId ->
            val entry = cached[fsId]
            entry == null || entry.expiresAtEpochSeconds - now <= PLAYBACK_SAFETY_SECONDS
        }
        val accessToken = authRepository.ensureValidAccessToken()
        val refreshed = missing.chunked(FILE_METAS_BATCH_SIZE).flatMap { batch ->
            val resolved = client.fileMetas(batch, accessToken)
            val resolvedIds = resolved.mapTo(mutableSetOf()) { it.fsId }
            val absent = batch.filterNot(resolvedIds::contains)
            check(absent.isEmpty()) { "百度未返回 ${absent.size} 个视频的播放地址" }
            resolved.map { DlinkCacheEntity(it.fsId, it.url, now, now + DLINK_TTL_SECONDS) }
        }
        if (refreshed.isNotEmpty()) cacheStore.save(refreshed)
        val all = cached + refreshed.associateBy { it.fsId }
        requested.associateWith { fsId ->
            val entry = checkNotNull(all[fsId]) { "播放地址不存在" }
            PlaybackSource(
                fsId = fsId,
                url = appendAccessToken(entry.url, accessToken),
                obtainedAtEpochSeconds = entry.obtainedAtEpochSeconds,
                expiresAtEpochSeconds = entry.expiresAtEpochSeconds,
                requiredHeaders = REQUIRED_HEADERS,
            )
        }
    }

    suspend fun invalidate(fsId: Long) = cacheStore.delete(fsId)

    internal fun appendAccessToken(url: String, accessToken: String): String {
        val parsed = url.toHttpUrl()
        if ("access_token" in parsed.queryParameterNames) return url
        return parsed.newBuilder().addQueryParameter("access_token", accessToken).build().toString()
    }

    private companion object {
        const val FILE_METAS_BATCH_SIZE = 100
        const val PLAYBACK_SAFETY_SECONDS = 5 * 60L
        const val DLINK_TTL_SECONDS = 8 * 60 * 60L
        val REQUIRED_HEADERS = mapOf("User-Agent" to "pan.baidu.com")
    }
}
