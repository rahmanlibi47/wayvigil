
package com.saferoute.data.remote

import com.saferoute.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiClient @Inject constructor(
    private val tokenManager: TokenManager
) {

    val client: HttpClient = HttpClient(Android) {

        install(ContentNegotiation) {
            json(
                Json {
                    prettyPrint = true
                    isLenient = true
                    ignoreUnknownKeys = true
                }
            )
        }

        install(Logging) {
            logger = object : Logger {
                override fun log(message: String) {
                    android.util.Log.d("SafeRoute API", message)
                }
            }
            level = if (BuildConfig.DEBUG) LogLevel.ALL else LogLevel.NONE
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 30000
        }

        install(Auth) {
            bearer {
                loadTokens {
                    tokenManager.getToken()?.let { token ->
                        BearerTokens(
                            accessToken = token,
                            refreshToken = ""
                        )
                    }
                }

                sendWithoutRequest {
                    true
                }
            }
        }

        defaultRequest {
            url(BuildConfig.API_BASE_URL)
            contentType(ContentType.Application.Json)
        }
    }

    companion object {
        const val API_VERSION = "/api/v1"
    }
}
