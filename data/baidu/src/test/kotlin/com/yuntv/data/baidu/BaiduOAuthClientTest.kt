package com.yuntv.data.baidu

import com.yuntv.data.baidu.BaiduOAuthClient.PollResult
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BaiduOAuthClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: BaiduOAuthClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = BaiduOAuthClient(
            client = OkHttpClient(),
            clientId = "client-id",
            clientSecret = "client-secret",
            endpoints = BaiduOAuthClient.OAuthEndpoints(
                deviceCode = server.url("/device/code"),
                token = server.url("/token"),
            ),
            nowEpochSeconds = { 1_000L },
        )
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun `device code request uses required parameters and clamps polling interval`() = runTest {
        server.enqueue(
            MockResponse.Builder().body("""
                {
                  "device_code": "device-1",
                  "user_code": "ABCD-EFGH",
                  "verification_url": "https://example.test/verify",
                  "qrcode_url": "https://example.test/qr",
                  "expires_in": 1800,
                  "interval": 1
                }
            """.trimIndent()).build(),
        )

        val result = client.requestDeviceCode()

        assertEquals("device-1", result.deviceCode)
        assertEquals(2_800L, result.expiresAtEpochSeconds)
        assertEquals(5L, result.intervalSeconds)
        val request = server.takeRequest()
        assertEquals("pan.baidu.com", request.headers["User-Agent"])
        assertEquals("device_code", request.url.queryParameter("response_type"))
        assertEquals("client-id", request.url.queryParameter("client_id"))
        assertEquals("basic,netdisk", request.url.queryParameter("scope"))
    }

    @Test
    fun `pending response is not treated as a failure`() = runTest {
        server.enqueue(
            MockResponse.Builder()
                .code(400)
                .body("""{"error":"authorization_pending"}""")
                .build(),
        )

        assertEquals(PollResult.Pending, client.pollDeviceToken("device-1"))
    }

    @Test
    fun `successful device token preserves rotated refresh token`() = runTest {
        server.enqueue(
            MockResponse.Builder().body("""
                {
                  "access_token": "access-1",
                  "refresh_token": "refresh-2",
                  "expires_in": 2592000,
                  "scope": "basic netdisk"
                }
            """.trimIndent()).build(),
        )

        val result = client.pollDeviceToken("device-1")

        assertTrue(result is PollResult.Success)
        val token = (result as PollResult.Success).tokenBundle
        assertEquals("access-1", token.accessToken)
        assertEquals("refresh-2", token.refreshToken)
        assertEquals(2_593_000L, token.expiresAtEpochSeconds)
        val request = server.takeRequest()
        assertEquals("device_token", request.url.queryParameter("grant_type"))
        assertEquals("client-secret", request.url.queryParameter("client_secret"))
    }

    @Test
    fun `refresh request atomically returns the new token pair`() = runTest {
        server.enqueue(
            MockResponse.Builder().body("""
                {
                  "access_token": "new-access",
                  "refresh_token": "new-refresh",
                  "expires_in": 3600
                }
            """.trimIndent()).build(),
        )

        val token = client.refresh("old-refresh")

        assertEquals("new-access", token.accessToken)
        assertEquals("new-refresh", token.refreshToken)
        val request = server.takeRequest()
        assertEquals("refresh_token", request.url.queryParameter("grant_type"))
        assertEquals("old-refresh", request.url.queryParameter("refresh_token"))
    }
}
