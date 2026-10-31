package com.saferoute.navigation

import com.saferoute.data.model.LocationData
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Filters GPS location readings to reduce noise and jitter.
 * Uses a simple distance-based filter rather than Kalman filter
 * (as per requirements, only implement Kalman if simple filtering proves inadequate).
 */
class LocationFilter(
    private val minDistanceMeters: Float = DEFAULT_MIN_DISTANCE_METERS,
    private val maxAgeMs: Long = DEFAULT_MAX_AGE_MS
) {
    private var lastLocation: LocationData? = null
    private var lastAcceptedLocation: LocationData? = null

    /**
     * Processes a new location reading and returns it if it passes filters.
     * Returns null if the reading should be rejected.
     */
    fun process(location: LocationData): LocationData? {
        // Check basic accuracy requirement
        if (!location.hasAcceptableAccuracy()) {
            return null
        }

        val lastLoc = lastAcceptedLocation

        // First location is always accepted
        if (lastLoc == null) {
            lastAcceptedLocation = location
            lastLocation = location
            return location
        }

        // Check if location is too old relative to last accepted
        val timeDelta = location.timestamp - lastLoc.timestamp
        if (timeDelta > maxAgeMs) {
            // Location is stale, accept it but reset tracking
            lastAcceptedLocation = location
            lastLocation = location
            return location
        }

        // Calculate distance from last accepted location
        val distance = calculateDistance(lastLoc, location)

        // Filter out micro-movements (GPS jitter when stationary)
        if (distance < minDistanceMeters && location.speed < 1f) {
            // Reject reading, but update last location for tracking
            lastLocation = location
            return null
        }

        // Accept the location
        lastAcceptedLocation = location
        lastLocation = location
        return location
    }

    /**
     * Calculates approximate distance between two locations in meters
     * Uses simplified equirectangular approximation (accurate enough for small distances)
     */
    fun calculateDistance(loc1: LocationData, loc2: LocationData): Float {
        val lat1Rad = Math.toRadians(loc1.latitude)
        val lat2Rad = Math.toRadians(loc2.latitude)
        val deltaLatRad = Math.toRadians(loc2.latitude - loc1.latitude)
        val deltaLonRad = Math.toRadians(loc2.longitude - loc1.longitude)

        val x = deltaLonRad * kotlin.math.cos((lat1Rad + lat2Rad) / 2)
        val y = deltaLatRad

        return (EARTH_RADIUS_METERS * sqrt(x * x + y * y)).toFloat()
    }

    /**
     * Calculates bearing from one location to another
     */
    fun calculateBearing(from: LocationData, to: LocationData): Float {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val lonDiff = Math.toRadians(to.longitude - from.longitude)

        val y = kotlin.math.sin(lonDiff) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
                kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(lonDiff)

        var bearing = Math.toDegrees(kotlin.math.atan2(y, x)).toFloat()
        if (bearing < 0) bearing += 360f

        return bearing
    }

    /**
     * Resets the filter
     */
    fun reset() {
        lastLocation = null
        lastAcceptedLocation = null
    }

    companion object {
        const val EARTH_RADIUS_METERS = 6371000.0
        const val DEFAULT_MIN_DISTANCE_METERS = 3f // Filter movements less than 3 meters
        const val DEFAULT_MAX_AGE_MS = 5000L // 5 seconds
    }
}
