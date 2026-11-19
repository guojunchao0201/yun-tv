package com.yuntv.data.baidu

import com.yuntv.core.model.AuthRepository
import com.yuntv.core.model.AuthState
import com.yuntv.core.model.TokenBundle
import com.yuntv.core.security.TokenVault
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

class DefaultAuthRepository(
    private val oauthClient: BaiduOAuthClient,
    private val tokenVault: TokenVault,
    private val applicationScope: CoroutineScope,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1_000L },
) : AuthRepository {
    private val state = MutableStateFlow<AuthState>(AuthState.Initializing)
    private val refreshMutex = Mutex()
    private val authorizationMutex = Mutex()
    private var currentToken: TokenBundle? = null
    @Volatile private var authorizationJob: Job? = null

    init {
        applicationScope.launch { restoreSession() }
    }

    override fun observeAuthState(): StateFlow<AuthState> = state

    override suspend fun authorize() {
        authorizationMutex.withLock {
            authorizationJob = coroutineContext[Job]
            try {
                if (!oauthClient.isConfigured) {
                    state.value = AuthState.ConfigurationMissing(
                        "请在项目根目录的 secrets.properties 配置百度 AppKey 和 SecretKey，然后重新构建安装。",
                    )
                    return
                }
                state.value = AuthState.RequestingDeviceCode
                val authorization = oauthClient.requestDeviceCode()
                var intervalSeconds = authorization.intervalSeconds

                while (true) {
                    val remainingBeforeWait = authorization.expiresAtEpochSeconds - nowEpochSeconds()
                    if (remainingBeforeWait <= 0) throw OAuthException(
                        "授权二维码已过期",
                        authorizationExpired = true,
                    )
                    repeat(minOf(intervalSeconds, remainingBeforeWait).toInt()) {
                        state.value = AuthState.WaitingForUser(
                            authorization = authorization,
                            secondsRemaining = maxOf(0, authorization.expiresAtEpochSeconds - nowEpochSeconds()),
                        )
                        delay(1_000)
                    }

                    when (val result = oauthClient.pollDeviceToken(authorization.deviceCode)) {
                        BaiduOAuthClient.PollResult.Pending -> Unit
                        BaiduOAuthClient.PollResult.SlowDown -> intervalSeconds += 5
                        is BaiduOAuthClient.PollResult.Success -> {
                            tokenVault.save(result.tokenBundle)
                            currentToken = result.tokenBundle
                            state.value = AuthState.Authenticated(result.tokenBundle.expiresAtEpochSeconds)
                            return
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                state.value = AuthState.NeedsLogin
                throw cancelled
            } catch (error: OAuthConfigurationException) {
                state.value = AuthState.ConfigurationMissing(error.message.orEmpty())
            } catch (error: Exception) {
                state.value = AuthState.Error(
                    message = error.userMessage(),
                    retryable = error !is OAuthException || !error.invalidCredentials,
                    authorizationExpired = (error as? OAuthException)?.authorizationExpired == true,
                )
            } finally {
                authorizationJob = null
            }
        }
    }

    override fun cancelAuthorization() {
        authorizationJob?.cancel()
        state.value = AuthState.NeedsLogin
    }

    override suspend fun ensureValidAccessToken(): String = refreshMutex.withLock {
        val token = currentToken ?: tokenVault.load()?.also { currentToken = it }
            ?: throw OAuthException("尚未登录百度网盘", invalidCredentials = true)
        val now = nowEpochSeconds()
        if (token.expiresAtEpochSeconds - now > REFRESH_WINDOW_SECONDS) return token.accessToken

        state.value = AuthState.Refreshing(token.expiresAtEpochSeconds)
        try {
            val refreshed = oauthClient.refresh(token.refreshToken)
            tokenVault.save(refreshed)
            currentToken = refreshed
            state.value = AuthState.Authenticated(refreshed.expiresAtEpochSeconds)
            refreshed.accessToken
        } catch (error: OAuthException) {
            if (error.invalidCredentials) {
                tokenVault.clear()
                currentToken = null
                state.value = AuthState.NeedsLogin
                throw error
            }
            keepUsableTokenOrThrow(token, now, error)
        } catch (error: IOException) {
            keepUsableTokenOrThrow(token, now, error)
        }
    }

    override suspend fun logout() {
        cancelAuthorization()
        refreshMutex.withLock {
            tokenVault.clear()
            currentToken = null
            state.value = AuthState.NeedsLogin
        }
    }

    private suspend fun restoreSession() {
        if (!oauthClient.isConfigured) {
            state.value = AuthState.ConfigurationMissing(
                "请在项目根目录的 secrets.properties 配置百度 AppKey 和 SecretKey，然后重新构建安装。",
            )
            return
        }
        val restored = tokenVault.load()
        currentToken = restored
        if (restored == null) {
            state.value = AuthState.NeedsLogin
            return
        }
        if (restored.expiresAtEpochSeconds - nowEpochSeconds() > REFRESH_WINDOW_SECONDS) {
            state.value = AuthState.Authenticated(restored.expiresAtEpochSeconds)
        } else {
            runCatching { ensureValidAccessToken() }
        }
    }

    private fun keepUsableTokenOrThrow(token: TokenBundle, now: Long, error: Exception): String {
        if (token.expiresAtEpochSeconds > now) {
            state.value = AuthState.OfflineWithCachedData(
                message = error.userMessage(),
                accessTokenExpiresAtEpochSeconds = token.expiresAtEpochSeconds,
            )
            return token.accessToken
        }
        state.value = AuthState.Error(error.userMessage(), retryable = true)
        throw error
    }

    private fun Throwable.userMessage(): String = when (this) {
        is OAuthException -> message ?: "百度授权失败"
        is IOException -> "网络连接失败，请检查网络后重试"
        else -> message ?: "百度授权失败"
    }

    private companion object {
        const val REFRESH_WINDOW_SECONDS = 24 * 60 * 60L
    }
}
