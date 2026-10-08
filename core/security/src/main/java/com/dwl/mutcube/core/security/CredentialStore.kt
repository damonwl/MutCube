package com.dwl.mutcube.core.security

interface CredentialStore {
    suspend fun read(providerId: String): String?

    suspend fun write(providerId: String, credential: String)

    suspend fun delete(providerId: String)
}

class CredentialStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
