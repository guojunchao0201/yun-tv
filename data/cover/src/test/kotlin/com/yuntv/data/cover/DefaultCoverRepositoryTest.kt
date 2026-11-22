package com.yuntv.data.cover

import com.yuntv.core.database.CoverCacheEntity
import com.yuntv.core.database.CoverFailureEntity
import com.yuntv.core.model.CoverSourceType
import com.yuntv.core.model.DriveItem
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultCoverRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var directory: File

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        directory = Files.createTempDirectory("yuntv-covers").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        directory.deleteRecursively()
    }

    @Test
    fun `downloads thumbnail with baidu user agent and saves persistent record`() = runTest {
        server.enqueue(MockResponse().setBody("image-bytes"))
        val store = FakeCoverStore()
        val generator = FakeGenerator()
        val repository = repository(store, generator)

        repository.requestCover(video(thumbnailUrl = server.url("/cover.jpg").toString()))
        advanceUntilIdle()

        val request = server.takeRequest()
        assertEquals("pan.baidu.com", request.getHeader("User-Agent"))
        val saved = store.get(7L)
        assertNotNull(saved)
        assertEquals(CoverSourceType.REMOTE_THUMBNAIL.name, saved?.sourceType)
        assertTrue(File(saved!!.localPath).isFile)
        assertEquals(1, generator.imageCalls)
    }

    @Test
    fun `valid persistent cover is reused and touched without network work`() = runTest {
        val store = FakeCoverStore()
        val localFile = File(directory, "cached.webp").apply { writeBytes(ByteArray(12)) }
        store.save(cache(fsId = 7L, modifiedAt = 20L, file = localFile, size = 12L))
        val generator = FakeGenerator()
        val repository = repository(store, generator)

        repository.requestCover(video())
        advanceUntilIdle()

        assertEquals(0, generator.imageCalls)
        assertEquals(0, generator.videoCalls)
        assertEquals(1_000L, store.get(7L)?.lastAccessedAtEpochSeconds)
        assertTrue(localFile.isFile)
    }

    @Test
    fun `invalidates modified cover and falls back to video frame`() = runTest {
        val store = FakeCoverStore()
        val staleFile = File(directory, "stale.webp").apply { writeBytes(ByteArray(12)) }
        store.save(cache(fsId = 7L, modifiedAt = 10L, file = staleFile, size = 12L))
        val generator = FakeGenerator()
        val repository = repository(store, generator)

        repository.requestCover(video(modifiedAt = 20L, thumbnailUrl = null), "https://video.test/dlink")
        advanceUntilIdle()

        assertFalse(staleFile.exists())
        assertEquals(1, generator.videoCalls)
        assertEquals("pan.baidu.com", generator.videoHeaders["User-Agent"])
        assertEquals(CoverSourceType.VIDEO_FRAME.name, store.get(7L)?.sourceType)
    }

    @Test
    fun `trims least recently used covers to ninety percent after overflow`() = runTest {
        server.enqueue(MockResponse().setBody("image"))
        val store = FakeCoverStore()
        val oldest = File(directory, "old.webp").apply { writeBytes(ByteArray(60)) }
        store.save(cache(fsId = 1L, modifiedAt = 1L, file = oldest, size = 60L, accessedAt = 1L))
        val repository = repository(store, FakeGenerator(outputSize = 50L), maxBytes = 100L)

        repository.requestCover(video(thumbnailUrl = server.url("/cover.jpg").toString()))
        advanceUntilIdle()

        assertFalse(oldest.exists())
        assertEquals(null, store.get(1L))
        assertNotNull(store.get(7L))
        assertTrue(store.totalFileSize() <= 90L)
    }

    @Test
    fun `recent complete failure suppresses regeneration for twenty four hours`() = runTest {
        val store = FakeCoverStore()
        store.saveFailure(CoverFailureEntity(7L, 20L, 999L))
        val generator = FakeGenerator()
        val repository = repository(store, generator)

        repository.requestCover(video(thumbnailUrl = null), "https://video.test/dlink")
        advanceUntilIdle()

        assertEquals(0, generator.videoCalls)
        assertEquals(null, store.get(7L))
    }

    private fun kotlinx.coroutines.test.TestScope.repository(
        store: CoverStore,
        generator: CoverGenerator,
        maxBytes: Long = 512L * 1024L * 1024L,
    ) = DefaultCoverRepository(
        store = store,
        coversDirectory = directory,
        httpClient = OkHttpClient(),
        applicationScope = this,
        generator = generator,
        maxCacheBytes = maxBytes,
        nowEpochSeconds = { 1_000L },
        workDispatcher = StandardTestDispatcher(testScheduler),
    )

    private fun video(
        modifiedAt: Long = 20L,
        thumbnailUrl: String? = "https://image.test/cover.jpg",
    ) = DriveItem(
        fsId = 7L,
        parentPath = "/apps/视频",
        path = "/apps/视频/movie.mp4",
        name = "movie.mp4",
        isDirectory = false,
        sizeBytes = 123L,
        modifiedAtEpochSeconds = modifiedAt,
        category = 1,
        mimeType = "video/mp4",
        thumbnailUrl = thumbnailUrl,
    )

    private fun cache(
        fsId: Long,
        modifiedAt: Long,
        file: File,
        size: Long,
        accessedAt: Long = 5L,
    ) = CoverCacheEntity(
        fsId = fsId,
        sourceModifiedAtEpochSeconds = modifiedAt,
        sourceType = CoverSourceType.REMOTE_THUMBNAIL.name,
        localPath = file.absolutePath,
        width = 640,
        height = 360,
        fileSizeBytes = size,
        createdAtEpochSeconds = accessedAt,
        lastAccessedAtEpochSeconds = accessedAt,
    )
}

private class FakeGenerator(private val outputSize: Long = 32L) : CoverGenerator {
    var imageCalls = 0
    var videoCalls = 0
    var videoHeaders: Map<String, String> = emptyMap()

    override suspend fun fromImage(bytes: ByteArray, destination: File): GeneratedCover {
        imageCalls++
        destination.writeBytes(ByteArray(outputSize.toInt()))
        return GeneratedCover(640, 360, outputSize)
    }

    override suspend fun fromVideo(
        url: String,
        headers: Map<String, String>,
        destination: File,
    ): GeneratedCover {
        videoCalls++
        videoHeaders = headers
        destination.writeBytes(ByteArray(outputSize.toInt()))
        return GeneratedCover(640, 360, outputSize)
    }
}

private class FakeCoverStore : CoverStore {
    private val covers = mutableMapOf<Long, CoverCacheEntity>()
    private val flows = mutableMapOf<Long, MutableStateFlow<CoverCacheEntity?>>()
    private val failures = mutableMapOf<Long, CoverFailureEntity>()

    override fun observe(fsId: Long): Flow<CoverCacheEntity?> =
        flows.getOrPut(fsId) { MutableStateFlow(covers[fsId]) }

    override suspend fun get(fsId: Long) = covers[fsId]
    override suspend fun save(entity: CoverCacheEntity) {
        covers[entity.fsId] = entity
        flows.getOrPut(entity.fsId) { MutableStateFlow(null) }.value = entity
    }
    override suspend fun touch(fsId: Long, accessedAt: Long) {
        covers[fsId]?.let { save(it.copy(lastAccessedAtEpochSeconds = accessedAt)) }
    }
    override suspend fun delete(fsId: Long) {
        covers.remove(fsId)
        flows.getOrPut(fsId) { MutableStateFlow(null) }.value = null
    }
    override suspend fun totalFileSize() = covers.values.sumOf { it.fileSizeBytes }
    override suspend fun oldestFirst() = covers.values.sortedBy { it.lastAccessedAtEpochSeconds }
    override suspend fun getFailure(fsId: Long) = failures[fsId]
    override suspend fun saveFailure(entity: CoverFailureEntity) {
        failures[entity.fsId] = entity
    }
    override suspend fun deleteFailure(fsId: Long) {
        failures.remove(fsId)
    }
}
