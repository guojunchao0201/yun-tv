package com.yuntv.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.yuntv.core.model.TokenBundle
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AndroidTokenVault(
    context: Context,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : TokenVault {
    private val tokenDirectory = File(context.noBackupFilesDir, "auth")
    private val tokenFile = File(tokenDirectory, "token_v1.json")
    private val temporaryFile = File(tokenDirectory, "token_v1.tmp")

    override suspend fun load(): TokenBundle? = withContext(Dispatchers.IO) {
        if (!tokenFile.isFile) return@withContext null

        runCatching {
            val envelope = json.decodeFromString<TokenEnvelope>(tokenFile.readText())
            require(envelope.version == ENVELOPE_VERSION)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getExistingKey() ?: error("Token key is missing"),
                GCMParameterSpec(GCM_TAG_BITS, Base64.decode(envelope.iv, Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(envelope.ciphertext, Base64.NO_WRAP))
            json.decodeFromString<StoredTokenBundle>(plaintext.decodeToString()).toDomain()
        }.getOrElse {
            clearFiles()
            null
        }
    }

    override suspend fun save(tokenBundle: TokenBundle) = withContext(Dispatchers.IO) {
        tokenDirectory.mkdirs()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val plaintext = json.encodeToString(StoredTokenBundle.from(tokenBundle)).encodeToByteArray()
        val envelope = TokenEnvelope(
            version = ENVELOPE_VERSION,
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            ciphertext = Base64.encodeToString(cipher.doFinal(plaintext), Base64.NO_WRAP),
        )
        temporaryFile.writeText(json.encodeToString(envelope))
        try {
            Files.move(
                temporaryFile.toPath(),
                tokenFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: Exception) {
            Files.move(
                temporaryFile.toPath(),
                tokenFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
        Unit
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        clearFiles()
        keyStore().deleteEntry(KEY_ALIAS)
    }

    private fun clearFiles() {
        temporaryFile.delete()
        tokenFile.delete()
    }

    private fun getExistingKey(): SecretKey? = keyStore().getKey(KEY_ALIAS, null) as? SecretKey

    private fun getOrCreateKey(): SecretKey = getExistingKey() ?: KeyGenerator
        .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE)
        .apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }
        .generateKey()

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

    @Serializable
    private data class TokenEnvelope(
        val version: Int,
        val iv: String,
        val ciphertext: String,
    )

    @Serializable
    private data class StoredTokenBundle(
        val accessToken: String,
        val refreshToken: String,
        val scope: String?,
        val obtainedAtEpochSeconds: Long,
        val expiresAtEpochSeconds: Long,
    ) {
        fun toDomain() = TokenBundle(
            accessToken = accessToken,
            refreshToken = refreshToken,
            scope = scope,
            obtainedAtEpochSeconds = obtainedAtEpochSeconds,
            expiresAtEpochSeconds = expiresAtEpochSeconds,
        )

        companion object {
            fun from(value: TokenBundle) = StoredTokenBundle(
                accessToken = value.accessToken,
                refreshToken = value.refreshToken,
                scope = value.scope,
                obtainedAtEpochSeconds = value.obtainedAtEpochSeconds,
                expiresAtEpochSeconds = value.expiresAtEpochSeconds,
            )
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "yuntv_token_key_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val ENVELOPE_VERSION = 1
    }
}
