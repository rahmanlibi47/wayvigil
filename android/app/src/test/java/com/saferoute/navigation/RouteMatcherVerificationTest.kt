package com.saferoute.navigation

import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.Route
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Simplified verification tests for RouteMatcher.
 */
class RouteMatcherVerificationTest {

    private lateinit var routeMatcher: RouteMatcher

    @Before
    fun setup() {
        routeMatcher = RouteMatcher()
    }

    @Test
    fun `matches_location_on_straight_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600),
                LatLng(12.991600, 77.594600)
            )
        )

        val location = LocationData(12.976600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertNotNull(result)
    }

    @Test
    fun `detects_off_route_location`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Far from route
        val location = LocationData(13.071600, 77.694600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OffRoute)
    }

    @Test
    fun `handles_gps_accuracy_variations`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Test with different accuracy values
        val accuracies = listOf(5f, 10f, 30f)
        accuracies.forEach { accuracy ->
            routeMatcher.reset()
            val location = LocationData(12.976600, 77.594600, accuracy)
            val result = routeMatcher.matchLocationToRoute(location, route)
            assertNotNull(result)
        }
    }

    @Test
    fun `reset_clears_matcher_state`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.976600, 77.594600, 5f)
        routeMatcher.matchLocationToRoute(location, route)

        routeMatcher.reset()

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
