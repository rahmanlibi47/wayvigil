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
    private val maxDeviationMeters: Float = DEFAULT_MAX_DEVIATION_METERS
) {

    private val locationFilter = LocationFilter()

    /**
     * Result of route matching
     */
    sealed class MatchResult {
        data class OnRoute(
            val progressFraction: Float,
            val distanceToRoute: Float,
            val nearestPoint: LatLng,
            val distanceToDestinationMeters: Float
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
        val match = findNearestPoint(currentLatLng, routePoints)

        // Calculate progress
        val progressFraction = if (routePoints.size > 1) {
            match.progressFraction
        } else {
            0f
        }

        // Check heading alignment if available
        val headingAligned = currentHeading?.let { heading ->
            isHeadingAligned(heading, match.segmentIndex, routePoints)
        } ?: true

        // Determine if on or off route
        return when {
            match.distanceMeters <= maxDeviationMeters &&
                (headingAligned || match.distanceToDestinationMeters <= ARRIVAL_PROXIMITY_METERS) -> {
                MatchResult.OnRoute(
                    progressFraction = progressFraction,
                    distanceToRoute = match.distanceMeters,
                    nearestPoint = match.point,
                    distanceToDestinationMeters = match.distanceToDestinationMeters
                )
            }
            match.distanceMeters <= maxDeviationMeters * 2 && headingAligned -> {
                // Slightly off but heading in right direction
                MatchResult.OffRoute(
                    distanceToRoute = match.distanceMeters,
                    nearestPoint = match.point,
                    suggestedAction = SuggestedAction.CONTINUE
                )
            }
            match.distanceMeters > maxDeviationMeters * 3 -> {
                // Way off route
                MatchResult.OffRoute(
                    distanceToRoute = match.distanceMeters,
                    nearestPoint = match.point,
                    suggestedAction = SuggestedAction.REROUTE
                )
            }
            else -> {
                MatchResult.OffRoute(
                    distanceToRoute = match.distanceMeters,
                    nearestPoint = match.point,
                    suggestedAction = SuggestedAction.CONTINUE
                )
            }
        }
    }

    /**
     * Finds the nearest point on the route to the given location
     */
    private data class RouteMatch(
        val point: LatLng,
        val segmentIndex: Int,
        val progressFraction: Float,
        val distanceMeters: Float,
        val distanceToDestinationMeters: Float
    )

    private fun findNearestPoint(
        location: LatLng,
        routePoints: List<LatLng>
    ): RouteMatch {
        if (routePoints.size == 1) {
            val point = routePoints.first()
            val distance = calculateDistance(location, point)
            return RouteMatch(point, 0, 0f, distance, distance)
        }

        val earthRadius = LocationFilter.EARTH_RADIUS_METERS
        val referenceLatitude = Math.toRadians(location.latitude)
        fun project(point: LatLng): Pair<Double, Double> {
            val x = Math.toRadians(point.longitude) * kotlin.math.cos(referenceLatitude) * earthRadius
            val y = Math.toRadians(point.latitude) * earthRadius
            return x to y
        }

        val locationPoint = project(location)
        val segmentLengths = routePoints.zipWithNext { start, end ->
            calculateDistance(start, end)
        }
        val totalRouteLength = segmentLengths.sum()
        var distanceBeforeSegment = 0f
        var best: RouteMatch? = null
        for (index in 0 until routePoints.lastIndex) {
            val start = routePoints[index]
            val end = routePoints[index + 1]
            val startXY = project(start)
            val endXY = project(end)
            val dx = endXY.first - startXY.first
            val dy = endXY.second - startXY.second
            val segmentLengthSquared = dx * dx + dy * dy
            val fraction = if (segmentLengthSquared == 0.0) {
                0.0
            } else {
                val locationDeltaX = locationPoint.first - startXY.first
                val locationDeltaY = locationPoint.second - startXY.second
                val projectedDistanceAlongSegment =
                    (locationDeltaX * dx + locationDeltaY * dy) / segmentLengthSquared
                projectedDistanceAlongSegment.coerceIn(0.0, 1.0)
            }
            val projected = LatLng(
                start.latitude + (end.latitude - start.latitude) * fraction,
                start.longitude + (end.longitude - start.longitude) * fraction
            )
            val distance = calculateDistance(location, projected)
            val currentBest = best
            if (currentBest == null || distance < currentBest.distanceMeters) {
                best = RouteMatch(
                    point = projected,
                    segmentIndex = index,
                    progressFraction = if (totalRouteLength > 0f) {
                        (distanceBeforeSegment + segmentLengths[index] * fraction.toFloat()) /
                            totalRouteLength
                    } else {
                        0f
                    },
                    distanceMeters = distance,
                    distanceToDestinationMeters = calculateDistance(location, routePoints.last())
                )
            }
            distanceBeforeSegment += segmentLengths[index]
        }
        return requireNotNull(best)
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
        var lat = 0L
        var lng = 0L

        fun readDelta(): Long? {
            var result = 0L
            var shift = 0
            while (true) {
                if (index >= len || shift > 30) return null
                val code = encoded[index++].code
                if (code !in 63..126) return null
                val value = code - 63
                result = result or ((value and 0x1f).toLong() shl shift)
                if (value < 0x20) break
                shift += 5
            }
            return if ((result and 1L) != 0L) (result shr 1).inv() else result shr 1
        }

        while (index < len) {
            val latitudeDelta = readDelta() ?: return emptyList()
            val longitudeDelta = readDelta() ?: return emptyList()
            lat += latitudeDelta
            lng += longitudeDelta
            if (lat !in -9_000_000L..9_000_000L || lng !in -18_000_000L..18_000_000L) {
                return emptyList()
            }

            val latLng = LatLng(lat / 1E5, lng / 1E5)
            poly.add(latLng)
        }

        return poly.takeIf { it.size >= 2 }.orEmpty()
    }

    fun reset() {
        locationFilter.reset()
    }

    companion object {
        const val DEFAULT_MAX_DEVIATION_METERS = 50f
        private const val ARRIVAL_PROXIMITY_METERS = 50f
    }
}
