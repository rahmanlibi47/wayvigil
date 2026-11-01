package com.saferoute.data.remote

import com.saferoute.data.model.Location
import com.saferoute.data.model.ManeuverType
import com.saferoute.data.model.Route
import com.saferoute.data.model.RouteStep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PlaceAutocompleteRequest(
    val input: String,
    @SerialName("session_token") val sessionToken: String,
    val latitude: Double? = null,
    val longitude: Double? = null
)

@Serializable
data class PlaceSuggestionResponse(
    @SerialName("place_id") val placeId: String,
    val name: String,
    val address: String
)

@Serializable
data class PlaceDetailsRequest(
    @SerialName("place_id") val placeId: String,
    @SerialName("session_token") val sessionToken: String
)

@Serializable
data class PlaceDetailsResponse(
    @SerialName("place_id") val placeId: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)

@Serializable
data class CoordinateDto(val latitude: Double, val longitude: Double)

@Serializable
data class ComputeRouteRequest(
    val origin: CoordinateDto,
    val destination: CoordinateDto,
    @SerialName("travel_mode") val travelMode: String = "DRIVE",
    @SerialName("compute_alternatives") val computeAlternatives: Boolean = true
)

@Serializable
data class NavigationRouteStepResponse(
    val instruction: String,
    @SerialName("distance_meters") val distanceMeters: Int,
    @SerialName("duration_seconds") val durationSeconds: Int,
    val maneuver: String,
    @SerialName("start_location") val startLocation: CoordinateDto,
    @SerialName("end_location") val endLocation: CoordinateDto,
    val polyline: String = ""
) {
    fun toDomainStep(): RouteStep = RouteStep(
        instruction = instruction,
        distanceMeters = distanceMeters,
        durationSeconds = durationSeconds,
        maneuver = ManeuverType.values().firstOrNull { it.name == maneuver } ?: ManeuverType.STRAIGHT,
        startLocation = Location(startLocation.latitude, startLocation.longitude),
        endLocation = Location(endLocation.latitude, endLocation.longitude),
        polyline = polyline
    )
}

@Serializable
data class NavigationRouteResponse(
    @SerialName("route_label") val routeLabel: String = "DEFAULT_ROUTE",
    val polyline: String,
    @SerialName("distance_meters") val distanceMeters: Int,
    @SerialName("duration_seconds") val durationSeconds: Int,
    val steps: List<NavigationRouteStepResponse>,
    val warnings: List<String> = emptyList()
) {
    fun toDomainRoute(): Route {
        val domainSteps = steps.map(NavigationRouteStepResponse::toDomainStep)
        return Route(
            polyline = polyline,
            distanceMeters = distanceMeters,
            durationSeconds = durationSeconds,
            steps = domainSteps,
            warnings = warnings,
            routeLabel = routeLabel,
            destinationLocation = domainSteps.lastOrNull()?.endLocation
        )
    }
}

@Serializable
data class NavigationRoutesResponse(val routes: List<NavigationRouteResponse>)
