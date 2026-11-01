package com.saferoute.data.remote

import com.saferoute.data.model.CreateHazardReportRequest
import com.saferoute.data.model.Hazard
import com.saferoute.data.model.HazardReport
import com.saferoute.data.model.Location
import com.saferoute.data.model.User
import com.saferoute.data.model.UserUpdateRequest
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafeRouteApi @Inject constructor(
    private val apiClient: ApiClient
) {

    private val client = apiClient.client
    private val basePath = ApiClient.API_VERSION

    // Auth endpoints
    suspend fun anonymousLogin(deviceId: String, appVersion: String? = null): TokenResponse {
        return client.post("$basePath/auth/anonymous") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("device_id" to deviceId, "app_version" to appVersion))
        }.body()
    }

    suspend fun getCurrentUser(): User {
        return client.get("$basePath/users/me").body()
    }

    // User endpoints
    suspend fun updateUser(update: UserUpdateRequest): User {
        return client.put("$basePath/users/me") {
            setBody(update)
        }.body()
    }

    suspend fun deleteUser() {
        client.delete("$basePath/users/me")
    }

    // Hazard endpoints
    suspend fun getNearbyHazards(
        location: Location,
        radius: Double = 500.0,
        includeUnverified: Boolean = false
    ): List<Hazard> {
        return client.get("$basePath/hazards/nearby") {
            parameter("latitude", location.latitude)
            parameter("longitude", location.longitude)
            parameter("radius", radius)
            parameter("include_unverified", includeUnverified)
        }.body()
    }

    suspend fun createHazardReport(request: CreateHazardReportRequest): HazardReport {
        return client.post("$basePath/hazards/reports") {
            setBody(request)
        }.body()
    }

    suspend fun getMyReports(limit: Int = 20, offset: Int = 0): List<HazardReport> {
        return client.get("$basePath/hazards/reports/me") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    }

    suspend fun getReport(reportId: String): HazardReport {
        return client.get("$basePath/hazards/reports/$reportId").body()
    }

    suspend fun deleteReport(reportId: String) {
        client.delete("$basePath/hazards/reports/$reportId")
    }

    suspend fun autocompletePlaces(
        input: String,
        sessionToken: String,
        latitude: Double? = null,
        longitude: Double? = null
    ): List<PlaceSuggestionResponse> {
        return client.post("$basePath/navigation/places/autocomplete") {
            setBody(PlaceAutocompleteRequest(input, sessionToken, latitude, longitude))
        }.body()
    }

    suspend fun getPlaceDetails(placeId: String, sessionToken: String): PlaceDetailsResponse {
        return client.post("$basePath/navigation/places/details") {
            setBody(PlaceDetailsRequest(placeId, sessionToken))
        }.body()
    }

    suspend fun calculateRoute(request: ComputeRouteRequest): NavigationRoutesResponse {
        return client.post("$basePath/navigation/routes") {
            setBody(request)
        }.body()
    }
}

@kotlinx.serialization.Serializable
data class TokenResponse(
    @kotlinx.serialization.SerialName("access_token")
    val accessToken: String,

    @kotlinx.serialization.SerialName("token_type")
    val tokenType: String,

    @kotlinx.serialization.SerialName("expires_in")
    val expiresIn: Int,

    val user: User
)
