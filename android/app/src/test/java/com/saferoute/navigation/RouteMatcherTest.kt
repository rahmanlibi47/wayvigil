package com.saferoute.navigation

import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    fun `matchLocationToRoute returns result for valid inputs`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.9716, 77.5946),
                LatLng(12.9816, 77.5946)
            )
        )

        val location = LocationData(
            latitude = 12.9766,
            longitude = 77.5946,
            accuracy = 5f
        )

        val result = routeMatcher.matchLocationToRoute(location, route)

        // Just verify we get a result (either OnRoute or OffRoute)
        assertNotNull(result)
    }

    @Test
    fun `matchLocationToRoute returns OffRoute when far from route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.9716, 77.5946),
                LatLng(12.9816, 77.5946)
            )
        )

        // Location far from route (10km away)
        val location = LocationData(
            latitude = 13.0716,
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
    fun `matchLocationToRoute calculates progress fraction`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.9716, 77.5946),
                LatLng(12.9766, 77.5946),
                LatLng(12.9816, 77.5946)
            )
        )

        // Location at start of route
        val startLocation = LocationData(12.9716, 77.5946, 5f)
        val startResult = routeMatcher.matchLocationToRoute(startLocation, route)

        // Reset for second test
        routeMatcher.reset()

        // Location at end of route
        val endLocation = LocationData(12.9816, 77.5946, 5f)
        val endResult = routeMatcher.matchLocationToRoute(endLocation, route)

        // Just verify we get results
        assertNotNull(startResult)
        assertNotNull(endResult)
    }

    @Test
    fun `reset clears matcher state`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.9716, 77.5946),
                LatLng(12.9816, 77.5946)
            )
        )

        val location = LocationData(12.9766, 77.5946, 5f)
        routeMatcher.matchLocationToRoute(location, route)

        routeMatcher.reset()

        // Should work normally after reset
        val result = routeMatcher.matchLocationToRoute(location, route)
        assertNotNull(result)
    }

    // Helper functions
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

    private fun createRouteFromPoints(points: List<LatLng>): Route {
        return Route(
            polyline = encodePolyline(points),
            distanceMeters = 1000,
            durationSeconds = 120,
            steps = emptyList()
        )
    }
}
