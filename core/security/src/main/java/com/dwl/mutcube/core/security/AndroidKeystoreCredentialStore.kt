package com.dwl.mutcube.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreCredentialStore(
    context: Context,
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
    preferencesName: String = DEFAULT_PREFERENCES_NAME,
) : CredentialStore {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    override suspend fun read(providerId: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = providerId.validatedKey()
            val encrypted = preferences.getString("$key.value", null) ?: return@withLock null
            val iv = preferences.getString("$key.iv", null)
                ?: throw CredentialStorageException("Credential IV is missing")
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(
                    Cipher.DECRYPT_MODE,
                    getOrCreateSecretKey(),
                    GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(iv, Base64.NO_WRAP)),
                )
                cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)).decodeToString()
            }.getOrElse { error ->
                throw CredentialStorageException("Unable to decrypt credential", error)
            }
        }
    }

    override suspend fun write(providerId: String, credential: String) = withContext(Dispatchers.IO) {
        val normalized = credential.trim()
        require(normalized.isNotEmpty()) { "Credential must not be blank" }
        mutex.withLock {
            val key = providerId.validatedKey()
            runCatching {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
                val encrypted = cipher.doFinal(normalized.encodeToByteArray())
                check(
                    preferences.edit()
                        .putString("$key.value", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                        .putString("$key.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                        .commit(),
                ) { "Unable to commit credential" }
            }.getOrElse { error ->
                throw CredentialStorageException("Unable to encrypt credential", error)
            }
        }
    }

    override suspend fun delete(providerId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val key = providerId.validatedKey()
            if (!preferences.edit().remove("$key.value").remove("$key.iv").commit()) {
                throw CredentialStorageException("Unable to delete credential")
            }
        }
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(keyAlias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun String.validatedKey(): String {
        require(matches(PROVIDER_ID_PATTERN)) { "Invalid provider id" }
        return this
    }

    companion object {
        const val DEFAULT_PROVIDER_ID = "mimo"
        private const val DEFAULT_KEY_ALIAS = "mutcube.credentials.v1"
        private const val DEFAULT_PREFERENCES_NAME = "encrypted_credentials"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private val PROVIDER_ID_PATTERN = Regex("[a-z0-9._-]{1,64}")
    }
}
