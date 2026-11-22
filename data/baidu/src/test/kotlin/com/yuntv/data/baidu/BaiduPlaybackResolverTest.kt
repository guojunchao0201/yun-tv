package com.yuntv.data.baidu

import com.yuntv.core.database.DlinkCacheEntity
import com.yuntv.core.model.AuthRepository
import com.yuntv.core.model.AuthState
import com.yuntv.core.model.DriveItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaiduPlaybackResolverTest {
    @Test
    fun `splits more than one hundred ids and caches for eight hours`() = runTest {
        val service = FakeFileService()
        val store = FakePlaybackStore()
        val resolver = resolver(service, store)
        val ids = (1L..205L).toList()

        val sources = resolver.resolve(ids)

        assertEquals(listOf(100, 100, 5), service.batches.map { it.size })
        assertEquals(205, sources.size)
        assertEquals(1_000L + 8 * 60 * 60L, store.entries.getValue(1L).expiresAtEpochSeconds)
        assertEquals("pan.baidu.com", sources.getValue(1L).requiredHeaders["User-Agent"])
        assertTrue(sources.getValue(1L).url.endsWith("access_token=access-token"))
    }

    @Test
    fun `reuses cache only when more than five minutes remain`() = runTest {
        val service = FakeFileService()
        val store = FakePlaybackStore().apply {
            entries[1L] = DlinkCacheEntity(1L, "https://video.test/1?x=1", 10L, 1_301L)
            entries[2L] = DlinkCacheEntity(2L, "https://video.test/2?x=1", 10L, 1_300L)
        }
        val resolver = resolver(service, store)

        val sources = resolver.resolve(listOf(1L, 2L))

        assertEquals(listOf(listOf(2L)), service.batches)
        assertEquals(10L, sources.getValue(1L).obtainedAtEpochSeconds)
        assertEquals(1_000L, sources.getValue(2L).obtainedAtEpochSeconds)
    }

    @Test
    fun `does not append duplicate access token`() = runTest {
        val resolver = resolver(FakeFileService(), FakePlaybackStore())
        val url = "https://video.test/file?access_token=existing"

        assertEquals(url, resolver.appendAccessToken(url, "new-token"))
    }

    private fun resolver(service: FakeFileService, store: FakePlaybackStore) = BaiduPlaybackResolver(
        client = service,
        authRepository = FakeAuthRepository(),
        cacheStore = store,
        nowEpochSeconds = { 1_000L },
    )
}

private class FakeFileService : BaiduFileService {
    val batches = mutableListOf<List<Long>>()

    override suspend fun listDirectory(path: String, accessToken: String): List<DriveItem> = emptyList()

    override suspend fun fileMetas(fsIds: List<Long>, accessToken: String): List<ResolvedDlink> {
        batches += fsIds
        return fsIds.map { ResolvedDlink(it, "https://video.test/$it?x=1") }
    }
}

private class FakePlaybackStore : PlaybackCacheStore {
    val entries = mutableMapOf<Long, DlinkCacheEntity>()
    override suspend fun get(fsIds: List<Long>) = fsIds.mapNotNull(entries::get)
    override suspend fun save(entries: List<DlinkCacheEntity>) {
        entries.forEach { this.entries[it.fsId] = it }
    }
    override suspend fun delete(fsId: Long) {
        entries.remove(fsId)
    }
}

private class FakeAuthRepository : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Authenticated(Long.MAX_VALUE))
    override fun observeAuthState(): StateFlow<AuthState> = state
    override suspend fun authorize() = Unit
    override suspend fun ensureValidAccessToken() = "access-token"
    override suspend fun logout() = Unit
    override fun cancelAuthorization() = Unit
}
