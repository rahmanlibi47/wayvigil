package com.saferoute.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val tokenManager: TokenManager,
    private val deviceIdManager: DeviceIdManager
) {
    fun Auth.Config.configureBearerAuth() {
        bearer {
            loadTokens {
                tokenManager.getToken()?.let { BearerTokens(it, "") }
            }
            refreshTokens {
                reauthenticate()?.also {
                    tokenManager.saveToken(it.accessToken)
                    tokenManager.saveUserId(it.user.id)
                } ?: run {
                    tokenManager.clearToken()
                    null
                }
            }
            sendWithoutRequest { true }
        }
    }

    private suspend fun reauthenticate(): BearerTokens? {
        return try {
            val response = anonymousLogin(deviceIdManager.getDeviceId())
                ?: return null
            BearerTokens(response.accessToken, "")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Session recovery failed", e)
            null
        }
    }

    private suspend fun anonymousLogin(deviceId: String): TokenResponse? {
        val client = HttpClient(Android) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            install(HttpTimeout) {
                requestTimeoutMillis = 30000
                connectTimeoutMillis = 10000
            }
            defaultRequest {
                url(com.saferoute.BuildConfig.API_BASE_URL)
                contentType(ContentType.Application.Json)
            }
        }
        return try {
            val response = client.post("${ApiClient.API_VERSION}/auth/anonymous") {
                setBody(mapOf("device_id" to deviceId))
            }
            if (response.status == HttpStatusCode.OK) response.body<TokenResponse>() else null
        } finally {
            client.close()
        }
    }

    companion object {
        private const val TAG = "AuthInterceptor"
    }
}
