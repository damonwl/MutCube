package com.dwl.mutcube.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.dwl.mutcube.core.extensions.McpServer
import com.dwl.mutcube.core.security.CredentialStore
import kotlinx.coroutines.suspendCancellableCoroutine
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class McpOAuthManager(context: Context, private val credentials: CredentialStore) {
    private val service = AuthorizationService(context.applicationContext)

    suspend fun authorizationIntent(server: McpServer): Intent {
        val oauth = requireNotNull(server.oauth) { "OAuth 配置不完整" }
        val configuration = discover(oauth.discoveryEndpoint)
        val request = AuthorizationRequest.Builder(
            configuration,
            oauth.clientId,
            ResponseTypeValues.CODE,
            REDIRECT_URI,
        ).setScopes(oauth.scopes.split(Regex("\\s+")).filter(String::isNotBlank)).build()
        return service.getAuthorizationRequestIntent(request)
    }

    suspend fun finishAuthorization(server: McpServer, data: Intent?): String = suspendCancellableCoroutine { continuation ->
        val intent = requireNotNull(data) { "授权已取消" }
        val response = AuthorizationResponse.fromIntent(intent)
        val authorizationError = AuthorizationException.fromIntent(intent)
        if (response == null) {
            if (continuation.isActive) {
                continuation.resumeWithException(authorizationError ?: IllegalStateException("授权没有返回结果"))
            }
            return@suspendCancellableCoroutine
        }
        val state = AuthState(response, authorizationError)
        service.performTokenRequest(response.createTokenExchangeRequest()) { token, error ->
            state.update(token, error)
            if (!continuation.isActive) return@performTokenRequest
            if (token == null || error != null) {
                continuation.resumeWithException(error ?: IllegalStateException("Token 交换失败"))
            } else {
                continuation.resume(state.jsonSerializeString())
            }
        }
    }.also { credentials.write(oauthKey(server.id), it) }

    suspend fun freshAccessToken(server: McpServer): String? {
        val serialized = credentials.read(oauthKey(server.id)) ?: return null
        val state = AuthState.jsonDeserialize(serialized)
        val token = suspendCancellableCoroutine<String> { continuation ->
            state.performActionWithFreshTokens(service) { accessToken, _, error ->
                if (!continuation.isActive) return@performActionWithFreshTokens
                if (accessToken != null) continuation.resume(accessToken)
                else continuation.resumeWithException(error ?: IllegalStateException("OAuth Token 不可用"))
            }
        }
        credentials.write(oauthKey(server.id), state.jsonSerializeString())
        return token
    }

    suspend fun clear(serverId: String) = credentials.delete(oauthKey(serverId))

    private suspend fun discover(endpoint: String): AuthorizationServiceConfiguration = suspendCancellableCoroutine { continuation ->
        AuthorizationServiceConfiguration.fetchFromUrl(Uri.parse(endpoint)) { configuration, error ->
            if (!continuation.isActive) return@fetchFromUrl
            if (configuration != null) continuation.resume(configuration)
            else continuation.resumeWithException(error ?: IllegalStateException("无法读取 OAuth 发现文档"))
        }
    }

    companion object {
        private val REDIRECT_URI = Uri.parse("com.dwl.mutcube:/oauth2redirect")
        private fun oauthKey(serverId: String) = "mcp-oauth.$serverId"
    }
}
