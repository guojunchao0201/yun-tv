package com.yuntv.core.model

import kotlinx.coroutines.flow.StateFlow

data class TokenBundle(
    val accessToken: String,
    val refreshToken: String,
    val scope: String?,
    val obtainedAtEpochSeconds: Long,
    val expiresAtEpochSeconds: Long,
)

data class DeviceAuthorization(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val qrCodeUrl: String,
    val expiresAtEpochSeconds: Long,
    val intervalSeconds: Long,
)

sealed interface AuthState {
    data object Initializing : AuthState
    data object NeedsLogin : AuthState
    data object RequestingDeviceCode : AuthState
    data class WaitingForUser(
        val authorization: DeviceAuthorization,
        val secondsRemaining: Long,
    ) : AuthState

    data class Authenticated(val expiresAtEpochSeconds: Long) : AuthState
    data class Refreshing(val expiresAtEpochSeconds: Long) : AuthState
    data class OfflineWithCachedData(
        val message: String,
        val accessTokenExpiresAtEpochSeconds: Long,
    ) : AuthState

    data class ConfigurationMissing(val message: String) : AuthState
    data class Error(
        val message: String,
        val retryable: Boolean,
        val authorizationExpired: Boolean = false,
    ) : AuthState
}

interface AuthRepository {
    fun observeAuthState(): StateFlow<AuthState>
    suspend fun authorize()
    suspend fun ensureValidAccessToken(): String
    suspend fun logout()
    fun cancelAuthorization()
}
