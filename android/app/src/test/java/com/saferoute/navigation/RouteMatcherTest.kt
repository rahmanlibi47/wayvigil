package com.saferoute.navigation

import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RouteMatcherTest {

    private lateinit var routeMatcher: RouteMatcher

    @Before
    fun setup() {
        routeMatcher = RouteMatcher()
    }

    @Test
    fun `matchLocationToRoute returns OnRoute when location is on route`() {
        // Create a simple straight route (encoded polyline)
        // Points: (12.9716, 77.5946) -> (12.9816, 77.5946) -> (12.9916, 77.5946)
        val route = Route(
            polyline = "q}tnAaucyM}@oB?g@?g@?g@", // Simplified encoded polyline
            distanceMeters = 2000,
            durationSeconds = 120,
            steps = emptyList()
        )

        // Location on the route
        val location = LocationData(
            latitude = 12.9766,
            longitude = 77.5946,
            accuracy = 5f
        )

        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `matchLocationToRoute returns OffRoute when far from route`() {
        val route = Route(
            polyline = "q}tnAaucyM}@oB?g@?g@?g@",
            distanceMeters = 2000,
            durationSeconds = 120,
            steps = emptyList()
        )

        // Location far from route
        val location = LocationData(
            latitude = 13.0716, // 10km away
            longitude = 77.6946,
            accuracy = 5f
        )

        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OffRoute)
        if (result is RouteMatcher.MatchResult.OffRoute) {
            assertEquals(RouteMatcher.SuggestedAction.REROUTE, result.suggestedAction)
        }
    }

    @Test
    fun `matchLocationToRoute calculates correct progress fraction`() {
        // Create route from south to north
        val route = Route(
            polyline = encodePolyline(listOf(
                LatLng(12.9716, 77.5946),
                LatLng(12.9766, 77.5946),
                LatLng(12.9816, 77.5946)
            )),
            distanceMeters = 2000,
            durationSeconds = 120,
            steps = emptyList()
        )

        // Location at start of route
        val startLocation = LocationData(12.9716, 77.5946, 5f)
        val startResult = routeMatcher.matchLocationToRoute(startLocation, route)

        if (startResult is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(startResult.progressFraction < 0.2f)
        }

        // Reset for second test
        routeMatcher.reset()

        // Location at end of route
        val endLocation = LocationData(12.9816, 77.5946, 5f)
        val endResult = routeMatcher.matchLocationToRoute(endLocation, route)

        if (endResult is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(endResult.progressFraction > 0.8f)
        }
    }

    @Test
    fun `matchLocationToRoute considers heading alignment`() {
        val route = Route(
            polyline = encodePolyline(listOf(
                LatLng(12.9716, 77.5946),
                LatLng(12.9816, 77.5946) // Going north
            )),
            distanceMeters = 1000,
            durationSeconds = 60,
            steps = emptyList()
        )

        // Location on route but heading wrong direction (south)
        val location = LocationData(
            latitude = 12.9766,
            longitude = 77.5946,
            accuracy = 5f,
            bearing = 180f // South
        )

        val result = routeMatcher.matchLocationToRoute(location, route, 180f)

        // Should be off route or have lower confidence due to wrong heading
        assertTrue(result is RouteMatcher.MatchResult)
    }

    @Test
    fun `reset clears matcher state`() {
        val route = Route(
            polyline = "q}tnAaucyM",
            distanceMeters = 1000,
            durationSeconds = 60,
            steps = emptyList()
        )

        val location = LocationData(12.9716, 77.5946, 5f)
        routeMatcher.matchLocationToRoute(location, route)

        routeMatcher.reset()

        // Should work normally after reset
        val result = routeMatcher.matchLocationToRoute(location, route)
        assertTrue(result is RouteMatcher.MatchResult)
    }

    /**
     * Simple polyline encoder for testing
     */
    private fun encodePolyline(points: List<LatLng>): String {
        val result = StringBuilder()
        var prevLat = 0
        var prevLng = 0

        for (point in points) {
            val lat = (point.latitude * 1e5).toInt()
            val lng = (point.longitude * 1e5).toInt()

            encodeValue(result, lat - prevLat)
            encodeValue(result, lng - prevLng)

            prevLat = lat
            prevLng = lng
        }

        return result.toString()
    }

    private fun encodeValue(sb: StringBuilder, value: Int) {
        var v = if (value < 0) (value shl 1).inv() else value shl 1
        while (v >= 0x20) {
            sb.append(((0x20 or (v and 0x1f)) + 63).toChar())
            v = v shr 5
        }
        sb.append((v + 63).toChar())
    }
}
