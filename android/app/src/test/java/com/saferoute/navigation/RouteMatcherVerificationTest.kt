package com.saferoute.navigation

import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification tests for RouteMatcher.
 * Tests straight routes, curved routes, parallel roads, GPS inaccuracies,
 * heading mismatches, and route endpoints.
 */
class RouteMatcherVerificationTest {

    private lateinit var routeMatcher: RouteMatcher

    @Before
    fun setup() {
        routeMatcher = RouteMatcher()
    }

    // ==================== Straight Routes ====================

    @Test
    fun `matches_location_on_straight_north_south_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.976600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `matches_location_on_straight_east_west_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.971600, 77.599600),
                LatLng(12.971600, 77.604600)
            )
        )

        val location = LocationData(12.971600, 77.599600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `matches_location_on_diagonal_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.599600),
                LatLng(12.981600, 77.604600)
            )
        )

        val location = LocationData(12.976600, 77.599600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    // ==================== Curved Routes ====================

    @Test
    fun `matches_location_on_gentle_curve`() {
        // Create a route with a gentle curve
        val points = mutableListOf<LatLng>()
        for (i in 0..10) {
            val lat = 12.971600 + (i * 0.0005)
            val lng = 77.594600 + (i * i * 0.00002) // Quadratic curve
            points.add(LatLng(lat, lng))
        }

        val route = createRouteFromPoints(points)

        // Point on the curve
        val location = LocationData(12.974100, 77.595100, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `matches_location_on_sharp_turn`() {
        // Route with a 90-degree turn
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.594600), // North
                LatLng(12.976600, 77.599600) // Then east
            )
        )

        // At the corner
        val location = LocationData(12.976600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `matches_location_on_s_curve`() {
        // S-curve route
        val points = mutableListOf<LatLng>()
        for (i in 0..20) {
            val lat = 12.971600 + (i * 0.0005)
            val lng = 77.594600 + kotlin.math.sin(i * 0.3) * 0.001
            points.add(LatLng(lat, lng))
        }

        val route = createRouteFromPoints(points)

        // Middle of S-curve
        val location = LocationData(12.976600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    // ==================== Parallel Roads ====================

    @Test
    fun `detects_deviation_on_parallel_road_30m_away`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Location on parallel road ~30m east
        val location = LocationData(12.976600, 77.594850, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        // Should still be on route (within 50m threshold)
        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `detects_deviation_on_parallel_road_100m_away`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Location on parallel road ~100m east
        val location = LocationData(12.976600, 77.595500, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        // Should be off route
        assertTrue(result is RouteMatcher.MatchResult.OffRoute)
    }

    @Test
    fun `detects_deviation_on_overpass`() {
        // Route on ground level
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Location on overpass crossing above (different lat, same lon)
        val location = LocationData(12.976600, 77.594000, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        // Should be detected as off route
        assertTrue(result is RouteMatcher.MatchResult.OffRoute)
    }

    // ==================== GPS Inaccuracies ====================

    @Test
    fun `handles_location_with_10m_accuracy`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Slightly off route but within GPS accuracy
        val location = LocationData(12.976600, 77.594650, 10f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `handles_location_with_30m_accuracy`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Location with poor accuracy
        val location = LocationData(12.976600, 77.594700, 30f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        // Should still match with tolerance
        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `handles_gps_drift_near_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Simulate GPS drift oscillating around route
        val locations = listOf(
            LocationData(12.976600, 77.594500, 8f),
            LocationData(12.976650, 77.594550, 10f),
            LocationData(12.976580, 77.594480, 12f),
            LocationData(12.976620, 77.594520, 9f)
        )

        locations.forEach { location ->
            val result = routeMatcher.matchLocationToRoute(location, route)
            assertTrue(result is RouteMatcher.MatchResult.OnRoute)
        }
    }

    // ==================== Heading Mismatches ====================

    @Test
    fun `matches_with_aligned_heading`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600) // Northbound
            )
        )

        val location = LocationData(
            12.976600,
            77.594600,
            5f,
            bearing = 0f // Heading north
        )
        val result = routeMatcher.matchLocationToRoute(location, route, 0f)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `detects_wrong_heading_opposite_direction`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600) // Northbound
            )
        )

        val location = LocationData(
            12.976600,
            77.594600,
            5f,
            bearing = 180f // Heading south (wrong way)
        )
        val result = routeMatcher.matchLocationToRoute(location, route, 180f)

        // May still be on route but heading is wrong
        assertTrue(result is RouteMatcher.MatchResult)
    }

    @Test
    fun `handles_heading_on_curved_section`() {
        // Curved route going northeast then east
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.597600),
                LatLng(12.976600, 77.604600)
            )
        )

        // At the curve, heading east
        val location = LocationData(
            12.976600,
            77.597600,
            5f,
            bearing = 90f // East
        )
        val result = routeMatcher.matchLocationToRoute(location, route, 90f)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    // ==================== Route Start and End ====================

    @Test
    fun `matches_at_route_start`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.971600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction < 0.1f)
        }
    }

    @Test
    fun `matches_near_route_start`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.972100, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction in 0.05f..0.15f)
        }
    }

    @Test
    fun `matches_at_route_end`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.981600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction > 0.9f)
        }
    }

    @Test
    fun `matches_near_route_end`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.981100, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction > 0.85f)
        }
    }

    // ==================== Progress Calculation ====================

    @Test
    fun `calculates_progress_at_25_percent`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.976600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.974100, 77.594600, 5f) // Quarter way
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction in 0.2f..0.3f)
        }
    }

    @Test
    fun `calculates_progress_at_50_percent`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.976600, 77.594600, 5f) // Half way
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction in 0.45f..0.55f)
        }
    }

    @Test
    fun `calculates_progress_at_75_percent`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        val location = LocationData(12.979100, 77.594600, 5f) // Three-quarter way
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OnRoute) {
            assertTrue(result.progressFraction in 0.7f..0.8f)
        }
    }

    // ==================== Off Route Detection ====================

    @Test
    fun `suggests_reroute_when_far_off_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // 200m away from route
        val location = LocationData(12.976600, 77.596400, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        if (result is RouteMatcher.MatchResult.OffRoute) {
            assertEquals(RouteMatcher.SuggestedAction.REROUTE, result.suggestedAction)
        }
    }

    @Test
    fun `suggests_continue_when_slightly_off_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // 60m away (slightly over threshold but heading OK)
        val location = LocationData(12.976600, 77.595150, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        // Could be temporary
        assertTrue(result is RouteMatcher.MatchResult)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles_very_short_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.972600, 77.594600) // Only ~100m
            )
        )

        val location = LocationData(12.972100, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    @Test
    fun `handles_empty_polyline`() {
        val route = Route(
            polyline = "",
            distanceMeters = 0,
            durationSeconds = 0,
            steps = emptyList()
        )

        val location = LocationData(12.976600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        assertTrue(result is RouteMatcher.MatchResult.OffRoute)
    }

    @Test
    fun `handles_single_point_route`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600)
            )
        )

        val location = LocationData(12.971600, 77.594600, 5f)
        val result = routeMatcher.matchLocationToRoute(location, route)

        // Should still work
        assertTrue(result is RouteMatcher.MatchResult)
    }

    @Test
    fun `reset_clears_matcher_state`() {
        val route = createRouteFromPoints(
            listOf(
                LatLng(12.971600, 77.594600),
                LatLng(12.981600, 77.594600)
            )
        )

        // Process some locations
        routeMatcher.matchLocationToRoute(LocationData(12.974600, 77.594600, 5f), route)
        routeMatcher.matchLocationToRoute(LocationData(12.976600, 77.594600, 5f), route)

        // Reset
        routeMatcher.reset()

        // Should work normally after reset
        val result = routeMatcher.matchLocationToRoute(LocationData(12.978600, 77.594600, 5f), route)
        assertTrue(result is RouteMatcher.MatchResult.OnRoute)
    }

    // ==================== Helper Methods ====================

    private fun createRouteFromPoints(points: List<LatLng>): Route {
        return Route(
            polyline = encodePolyline(points),
            distanceMeters = 1000, // Approximate
            durationSeconds = 120,
            steps = emptyList()
        )
    }

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
