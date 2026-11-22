package com.yuntv.data.baidu

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BaiduFileClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BaiduFileClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = BaiduFileClient(
            client = OkHttpClient(),
            endpoint = server.url("/rest/2.0/xpan/file"),
        )
    }

    @After
    fun tearDown() = server.close()

    @Test
    fun `list maps file metadata and required request parameters`() = runTest {
        server.enqueue(MockResponse.Builder().body("""
            {"errno":0,"list":[{
              "fs_id":42,"server_filename":"第 2 集.mp4","path":"/apps/视频/剧/第 2 集.mp4",
              "isdir":0,"size":1234,"server_mtime":99,"category":1,"media_type":"video/mp4",
              "thumbs":{"url3":"https://img.test/cover.jpg"}
            }]}
        """.trimIndent()).build())

        val items = client.listDirectory("/apps/视频/剧", "token-value")

        assertEquals(1, items.size)
        assertEquals(42L, items.single().fsId)
        assertFalse(items.single().isDirectory)
        assertEquals("https://img.test/cover.jpg", items.single().thumbnailUrl)
        val request = server.takeRequest()
        assertEquals("pan.baidu.com", request.headers["User-Agent"])
        assertEquals("list", request.url.queryParameter("method"))
        assertEquals("/apps/视频/剧", request.url.queryParameter("dir"))
        assertEquals("1000", request.url.queryParameter("limit"))
        assertEquals("token-value", request.url.queryParameter("access_token"))
    }

    @Test
    fun `exactly one thousand results requests the next page`() = runTest {
        val entries = (1..1_000).joinToString(",") { index ->
            "{\"fs_id\":$index,\"server_filename\":\"$index\",\"path\":\"/d/$index\",\"isdir\":1}"
        }
        server.enqueue(MockResponse.Builder().body("{\"errno\":0,\"list\":[$entries]}").build())
        server.enqueue(MockResponse.Builder().body("{\"errno\":0,\"list\":[]}").build())

        assertEquals(1_000, client.listDirectory("/d", "token").size)
        assertEquals("0", server.takeRequest().url.queryParameter("start"))
        assertEquals("1000", server.takeRequest().url.queryParameter("start"))
    }

    @Test
    fun `json errno is reported even when http succeeds`() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"errno":31066,"request_id":"req-1"}""").build())

        val error = runCatching { client.listDirectory("/missing", "token") }.exceptionOrNull()

        assertTrue(error is BaiduFileException)
        error as BaiduFileException
        assertEquals(31066, error.errno)
        assertEquals("req-1", error.requestId)
    }
}
