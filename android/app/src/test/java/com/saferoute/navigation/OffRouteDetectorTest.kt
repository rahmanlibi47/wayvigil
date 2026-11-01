package com.saferoute.navigation

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteDetectorTest {

    @Test
    fun `requires three distinct consecutive off-route fixes`() {
        val detector = OffRouteDetector()
        val offRoute = offRouteResult(RouteMatcher.SuggestedAction.REROUTE)

        assertFalse(detector.observe(offRoute, timestamp = 1_000L))
        assertFalse(detector.observe(offRoute, timestamp = 1_000L))
        assertFalse(detector.observe(offRoute, timestamp = 2_000L))
        assertTrue(detector.observe(offRoute, timestamp = 3_000L))
    }

    @Test
    fun `gps check does not count as a deviation or clear valid deviation fixes`() {
        val detector = OffRouteDetector()
        val offRoute = offRouteResult(RouteMatcher.SuggestedAction.REROUTE)
        val uncertain = offRouteResult(RouteMatcher.SuggestedAction.CHECK_GPS)

        assertFalse(detector.observe(offRoute, timestamp = 1_000L))
        assertFalse(detector.observe(uncertain, timestamp = 2_000L))
        assertFalse(detector.observe(offRoute, timestamp = 3_000L))
        assertTrue(detector.observe(offRoute, timestamp = 4_000L))
    }

    @Test
    fun `on-route fix and long gps gap reset deviation confirmation`() {
        val detector = OffRouteDetector()
        val offRoute = offRouteResult(RouteMatcher.SuggestedAction.REROUTE)
        val onRoute = RouteMatcher.MatchResult.OnRoute(
            progressFraction = 0.2f,
            distanceToRoute = 0f,
            nearestPoint = LatLng(0.0, 0.0),
            distanceToDestinationMeters = 800f
        )

        assertFalse(detector.observe(offRoute, timestamp = 1_000L))
        assertFalse(detector.observe(offRoute, timestamp = 2_000L))
        assertFalse(detector.observe(onRoute, timestamp = 3_000L))
        assertFalse(detector.observe(offRoute, timestamp = 4_000L))
        assertFalse(detector.observe(offRoute, timestamp = 10_000L))
        assertFalse(detector.observe(offRoute, timestamp = 11_000L))
        assertTrue(detector.observe(offRoute, timestamp = 12_000L))
    }

    private fun offRouteResult(action: RouteMatcher.SuggestedAction) =
        RouteMatcher.MatchResult.OffRoute(
            distanceToRoute = 200f,
            nearestPoint = LatLng(0.0, 0.0),
            suggestedAction = action
        )
}
