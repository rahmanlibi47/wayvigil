package com.saferoute.navigation

import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.Route
import kotlin.math.abs

/**
 * Determines if user's current location is on or near the planned route.
 * Used for deviation detection and progress tracking.
 */
class RouteMatcher(
    private val maxDeviationMeters: Float = DEFAULT_MAX_DEVIATION_METERS,
    private val minHeadingConfidence: Float = DEFAULT_MIN_HEADING_CONFIDENCE
) {

    private val locationFilter = LocationFilter()

    /**
     * Result of route matching
     */
    sealed class MatchResult {
        data class OnRoute(
            val progressFraction: Float,
            val distanceToRoute: Float,
            val nearestPoint: LatLng
        ) : MatchResult()

        data class OffRoute(
            val distanceToRoute: Float,
            val nearestPoint: LatLng,
            val suggestedAction: SuggestedAction
        ) : MatchResult()
    }

    enum class SuggestedAction {
        CONTINUE, // May be temporary, keep current route
        REROUTE, // Significantly off route, recalculate
        CHECK_GPS // Low confidence, may be GPS error
    }

    /**
     * Matches current location against the route
     */
    fun matchLocationToRoute(
        location: LocationData,
        route: Route,
        currentHeading: Float? = null
    ): MatchResult {
        // Filter location first
        val filteredLocation = locationFilter.process(location)
            ?: return MatchResult.OffRoute(
                distanceToRoute = Float.MAX_VALUE,
                nearestPoint = LatLng(location.latitude, location.longitude),
                suggestedAction = SuggestedAction.CHECK_GPS
            )

        // Decode polyline to get route points
        val routePoints = decodePolyline(route.polyline)
        if (routePoints.isEmpty()) {
            return MatchResult.OffRoute(
                distanceToRoute = Float.MAX_VALUE,
                nearestPoint = LatLng(location.latitude, location.longitude),
                suggestedAction = SuggestedAction.REROUTE
            )
        }

        // Find nearest point on route
        val currentLatLng = LatLng(filteredLocation.latitude, filteredLocation.longitude)
        val (nearestPoint, nearestIndex, minDistance) = findNearestPoint(currentLatLng, routePoints)

        // Calculate progress
        val progressFraction = nearestIndex.toFloat() / routePoints.size.coerceAtLeast(1)

        // Check heading alignment if available
        val headingAligned = currentHeading?.let { heading ->
            isHeadingAligned(heading, nearestIndex, routePoints)
        } ?: true

        // Determine if on or off route
        return when {
            minDistance <= maxDeviationMeters && headingAligned -> {
                MatchResult.OnRoute(
                    progressFraction = progressFraction,
                    distanceToRoute = minDistance,
                    nearestPoint = nearestPoint
                )
            }
            minDistance <= maxDeviationMeters * 2 && headingAligned -> {
                // Slightly off but heading in right direction
                MatchResult.OffRoute(
                    distanceToRoute = minDistance,
                    nearestPoint = nearestPoint,
                    suggestedAction = SuggestedAction.CONTINUE
                )
            }
            minDistance > maxDeviationMeters * 3 -> {
                // Way off route
                MatchResult.OffRoute(
                    distanceToRoute = minDistance,
                    nearestPoint = nearestPoint,
                    suggestedAction = SuggestedAction.REROUTE
                )
            }
            else -> {
                MatchResult.OffRoute(
                    distanceToRoute = minDistance,
                    nearestPoint = nearestPoint,
                    suggestedAction = SuggestedAction.CONTINUE
                )
            }
        }
    }

    /**
     * Finds the nearest point on the route to the given location
     */
    private fun findNearestPoint(
        location: LatLng,
        routePoints: List<LatLng>
    ): Triple<LatLng, Int, Float> {
        var minDistance = Float.MAX_VALUE
        var nearestPoint = routePoints.first()
        var nearestIndex = 0

        routePoints.forEachIndexed { index, point ->
            val distance = calculateDistance(location, point)
            if (distance < minDistance) {
                minDistance = distance
                nearestPoint = point
                nearestIndex = index
            }
        }

        return Triple(nearestPoint, nearestIndex, minDistance)
    }

    /**
     * Calculates distance between two LatLng points in meters
     */
    private fun calculateDistance(p1: LatLng, p2: LatLng): Float {
        val lat1Rad = Math.toRadians(p1.latitude)
        val lat2Rad = Math.toRadians(p2.latitude)
        val deltaLatRad = Math.toRadians(p2.latitude - p1.latitude)
        val deltaLonRad = Math.toRadians(p2.longitude - p1.longitude)

        val a = kotlin.math.sin(deltaLatRad / 2) * kotlin.math.sin(deltaLatRad / 2) +
            kotlin.math.cos(lat1Rad) * kotlin.math.cos(lat2Rad) *
            kotlin.math.sin(deltaLonRad / 2) * kotlin.math.sin(deltaLonRad / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))

        return (LocationFilter.EARTH_RADIUS_METERS * c).toFloat()
    }

    /**
     * Checks if heading aligns with route direction at given index
     */
    private fun isHeadingAligned(
        currentHeading: Float,
        routeIndex: Int,
        routePoints: List<LatLng>
    ): Boolean {
        if (routeIndex >= routePoints.size - 1) return true

        val current = routePoints[routeIndex]
        val next = routePoints[routeIndex + 1]

        // Calculate route heading
        val lat1 = Math.toRadians(current.latitude)
        val lat2 = Math.toRadians(next.latitude)
        val lonDiff = Math.toRadians(next.longitude - current.longitude)

        val y = kotlin.math.sin(lonDiff) * kotlin.math.cos(lat2)
        val x = kotlin.math.cos(lat1) * kotlin.math.sin(lat2) -
            kotlin.math.sin(lat1) * kotlin.math.cos(lat2) * kotlin.math.cos(lonDiff)

        var routeHeading = Math.toDegrees(kotlin.math.atan2(y, x)).toFloat()
        if (routeHeading < 0) routeHeading += 360f

        // Check if headings are aligned (allowing for 90-degree difference for perpendicular roads)
        val headingDiff = abs(normalizeHeading(currentHeading - routeHeading))
        return headingDiff < 90f || headingDiff > 270f
    }

    /**
     * Normalizes heading to 0-360 range
     */
    private fun normalizeHeading(heading: Float): Float {
        var result = heading % 360f
        if (result < 0) result += 360f
        return result
    }

    /**
     * Decodes an encoded polyline string into LatLng points
     */
    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val latLng = LatLng(lat / 1E5, lng / 1E5)
            poly.add(latLng)
        }

        return poly
    }

    fun reset() {
        locationFilter.reset()
    }

    companion object {
        const val DEFAULT_MAX_DEVIATION_METERS = 50f
        const val DEFAULT_MIN_HEADING_CONFIDENCE = 0.7f
    }
}
