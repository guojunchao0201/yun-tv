package com.yuntv.data.baidu

import com.yuntv.core.model.DeviceAuthorization
import com.yuntv.core.model.TokenBundle
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class BaiduOAuthClient(
    private val client: OkHttpClient,
    private val clientId: String,
    private val clientSecret: String,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val endpoints: OAuthEndpoints = OAuthEndpoints(),
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) {
    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientSecret.isNotBlank()

    suspend fun requestDeviceCode(): DeviceAuthorization = withContext(Dispatchers.IO) {
        requireConfigured()
        val url = endpoints.deviceCode.newBuilder()
            .addQueryParameter("response_type", "device_code")
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("scope", "basic,netdisk")
            .build()
        val response = execute<DeviceCodeResponse>(url)
        response.throwIfError()
        val deviceCode = response.deviceCode ?: throw OAuthException("百度未返回 device_code")
        val expiresIn = response.expiresIn ?: throw OAuthException("百度未返回二维码有效期")
        DeviceAuthorization(
            deviceCode = deviceCode,
            userCode = response.userCode.orEmpty(),
            verificationUrl = response.verificationUrl.orEmpty(),
            qrCodeUrl = response.qrCodeUrl ?: response.verificationUrl.orEmpty(),
            expiresAtEpochSeconds = nowEpochSeconds() + expiresIn,
            intervalSeconds = maxOf(MIN_POLL_INTERVAL_SECONDS, response.interval ?: 0L),
        )
    }

    suspend fun pollDeviceToken(deviceCode: String): PollResult = withContext(Dispatchers.IO) {
        requireConfigured()
        val url = endpoints.token.newBuilder()
            .addQueryParameter("grant_type", "device_token")
            .addQueryParameter("code", deviceCode)
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("client_secret", clientSecret)
            .build()
        val response = execute<TokenResponse>(url)
        when (response.error) {
            null, "" -> PollResult.Success(response.toTokenBundle(nowEpochSeconds()))
            "authorization_pending" -> PollResult.Pending
            "slow_down" -> PollResult.SlowDown
            "expired_token" -> throw OAuthException("授权二维码已过期", authorizationExpired = true)
            "access_denied" -> throw OAuthException("用户拒绝了百度网盘授权")
            else -> throw response.toException()
        }
    }

    suspend fun refresh(refreshToken: String): TokenBundle = withContext(Dispatchers.IO) {
        requireConfigured()
        val url = endpoints.token.newBuilder()
            .addQueryParameter("grant_type", "refresh_token")
            .addQueryParameter("refresh_token", refreshToken)
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("client_secret", clientSecret)
            .build()
        val response = execute<TokenResponse>(url)
        if (!response.error.isNullOrBlank()) throw response.toException()
        response.toTokenBundle(nowEpochSeconds())
    }

    private inline fun <reified T> execute(url: HttpUrl): T {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (body.isBlank()) throw IOException("百度 OAuth 返回空响应（HTTP ${response.code}）")
            return runCatching { json.decodeFromString<T>(body) }
                .getOrElse { throw OAuthException("无法解析百度 OAuth 响应", cause = it) }
        }
    }

    private fun requireConfigured() {
        if (!isConfigured) throw OAuthConfigurationException()
    }

    sealed interface PollResult {
        data object Pending : PollResult
        data object SlowDown : PollResult
        data class Success(val tokenBundle: TokenBundle) : PollResult
    }

    data class OAuthEndpoints(
        val deviceCode: HttpUrl = "https://openapi.baidu.com/oauth/2.0/device/code".toHttpUrl(),
        val token: HttpUrl = "https://openapi.baidu.com/oauth/2.0/token".toHttpUrl(),
    )

    @Serializable
    private data class DeviceCodeResponse(
        @SerialName("device_code") val deviceCode: String? = null,
        @SerialName("user_code") val userCode: String? = null,
        @SerialName("verification_url") val verificationUrl: String? = null,
        @SerialName("qrcode_url") val qrCodeUrl: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        val interval: Long? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    ) {
        fun throwIfError() {
            if (!error.isNullOrBlank()) throw OAuthException(errorDescription ?: error)
        }
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        val scope: String? = null,
        val error: String? = null,
        @SerialName("error_description") val errorDescription: String? = null,
    ) {
        fun toTokenBundle(now: Long): TokenBundle {
            val access = accessToken ?: throw OAuthException("百度未返回 access_token")
            val refresh = refreshToken ?: throw OAuthException("百度未返回 refresh_token")
            val lifetime = expiresIn ?: throw OAuthException("百度未返回 Token 有效期")
            return TokenBundle(
                accessToken = access,
                refreshToken = refresh,
                scope = scope,
                obtainedAtEpochSeconds = now,
                expiresAtEpochSeconds = now + lifetime,
            )
        }

        fun toException() = OAuthException(
            message = errorDescription ?: error ?: "百度 OAuth 请求失败",
            invalidCredentials = error in INVALID_CREDENTIAL_ERRORS,
            authorizationExpired = error == "expired_token",
        )
    }

    private companion object {
        const val USER_AGENT = "pan.baidu.com"
        const val MIN_POLL_INTERVAL_SECONDS = 5L
        val INVALID_CREDENTIAL_ERRORS = setOf("invalid_grant", "invalid_client", "invalid_token")
    }
}

class OAuthConfigurationException : IllegalStateException("请先在 secrets.properties 配置百度 AppKey 和 SecretKey")

class OAuthException(
    message: String,
    val invalidCredentials: Boolean = false,
    val authorizationExpired: Boolean = false,
    cause: Throwable? = null,
) : IOException(message, cause)
