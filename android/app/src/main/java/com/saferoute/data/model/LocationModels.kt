package com.saferoute.data.model

import kotlinx.serialization.Serializable

/**
 * Represents a geographic location with speed and accuracy information.
 */
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double = 0.0,
    val speed: Float = 0f,
    val speedAccuracy: Float = 0f,
    val bearing: Float = 0f,
    val bearingAccuracy: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * Speed in km/h (converted from m/s)
     */
    val speedKmh: Float
        get() = speed * 3.6f

    /**
     * Checks if this location has valid speed information
     */
    fun hasValidSpeed(): Boolean {
        return speed >= 0 && speedAccuracy >= 0 && speedAccuracy < SPEED_ACCURACY_THRESHOLD
    }

    /**
     * Checks if location accuracy is acceptable for navigation
     */
    fun hasAcceptableAccuracy(): Boolean {
        return accuracy in MIN_ACCURACY_METERS..MAX_ACCURACY_METERS
    }

    companion object {
        const val MIN_ACCURACY_METERS = 0f
        const val MAX_ACCURACY_METERS = 50f
        const val SPEED_ACCURACY_THRESHOLD = 5f // m/s
    }
}

/**
 * Speed reading with quality assessment
 */
data class SpeedReading(
    val speedKmh: Float,
    val accuracy: Float,
    val timestamp: Long
) {
    /**
     * Quality score from 0.0 to 1.0 based on accuracy
     */
    val quality: Float
        get() = when {
            accuracy < 1f -> 1.0f
            accuracy < 2f -> 0.9f
            accuracy < 3f -> 0.7f
            accuracy < 5f -> 0.5f
            else -> 0.3f
        }
}

/**
 * Navigation state
 */
sealed class NavigationState {
    data object Idle : NavigationState()
    data object Searching : NavigationState()
    data class RouteCalculated(val route: Route) : NavigationState()
    data class Navigating(
        val route: Route,
        val currentStep: Int,
        val remainingDistance: Int,
        val remainingDuration: Int
    ) : NavigationState()
    data class Deviation(val lastKnownRoute: Route) : NavigationState()
    data class Rerouting(val lastKnownRoute: Route) : NavigationState()
    data class Arrived(val destination: String) : NavigationState()
}

/**
 * Route information
 */
@Serializable
data class Route(
    val polyline: String, // Encoded polyline
    val distanceMeters: Int,
    val durationSeconds: Int,
    val steps: List<RouteStep>,
    val warnings: List<String> = emptyList()
)

/**
 * Individual route step (maneuver)
 */
@Serializable
data class RouteStep(
    val instruction: String,
    val distanceMeters: Int,
    val durationSeconds: Int,
    val maneuver: ManeuverType,
    val startLocation: Location,
    val endLocation: Location,
    val polyline: String
)

/**
 * Maneuver types
 */
enum class ManeuverType {
    DEPART,
    STRAIGHT,
    TURN_LEFT,
    TURN_RIGHT,
    UTURN_LEFT,
    UTURN_RIGHT,
    TURN_SLIGHT_LEFT,
    TURN_SLIGHT_RIGHT,
    TURN_SHARP_LEFT,
    TURN_SHARP_RIGHT,
    RAMP_LEFT,
    RAMP_RIGHT,
    MERGE,
    FORK_LEFT,
    FORK_RIGHT,
    ROUNDABOUT_LEFT,
    ROUNDABOUT_RIGHT,
    ARRIVE
}
