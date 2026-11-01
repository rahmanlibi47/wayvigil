package com.saferoute.navigation

import com.saferoute.data.model.Location
import com.saferoute.data.model.ManeuverType
import com.saferoute.data.model.Route
import com.saferoute.data.model.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressTrackerTest {

    @Test
    fun `remaining distance and duration follow route progress and never move backwards`() {
        val tracker = RouteProgressTracker(createRoute())

        val halfway = tracker.update(progressFraction = 0.5f, distanceToDestinationMeters = 500f)
        val regressed = tracker.update(progressFraction = 0.4f, distanceToDestinationMeters = 600f)

        assertEquals(500, halfway.remainingDistanceMeters)
        assertEquals(300, halfway.remainingDurationSeconds)
        assertEquals(500, regressed.remainingDistanceMeters)
        assertEquals(300, regressed.remainingDurationSeconds)
        assertFalse(halfway.arrived)
    }

    @Test
    fun `arrival requires near-end progress and proximity to destination`() {
        val tracker = RouteProgressTracker(createRoute())

        val nearEndButFarAway = tracker.update(
            progressFraction = 0.95f,
            distanceToDestinationMeters = 80f
        )
        val nearDestination = tracker.update(
            progressFraction = 0.96f,
            distanceToDestinationMeters = 34f
        )

        assertFalse(nearEndButFarAway.arrived)
        assertTrue(nearDestination.arrived)
    }

    @Test
    fun `upcoming maneuver advances as progress passes step distance`() {
        val route = createRoute(
            steps = listOf(
                createStep("Continue straight", 300, ManeuverType.DEPART),
                createStep("Turn left", 700, ManeuverType.TURN_LEFT)
            )
        )
        val tracker = RouteProgressTracker(route)

        val beforeTurn = tracker.update(progressFraction = 0.25f, distanceToDestinationMeters = 750f)
        val afterTurn = tracker.update(progressFraction = 0.35f, distanceToDestinationMeters = 650f)

        assertEquals(0, beforeTurn.currentStep)
        assertEquals(50, beforeTurn.distanceToNextManeuverMeters)
        assertEquals(1, afterTurn.currentStep)
        assertEquals(650, afterTurn.distanceToNextManeuverMeters)
    }

    private fun createRoute(steps: List<RouteStep> = emptyList()) = Route(
        polyline = "",
        distanceMeters = 1_000,
        durationSeconds = 600,
        steps = steps
    )

    private fun createStep(instruction: String, distance: Int, maneuver: ManeuverType) = RouteStep(
        instruction = instruction,
        distanceMeters = distance,
        durationSeconds = 60,
        maneuver = maneuver,
        startLocation = Location(0.0, 0.0),
        endLocation = Location(0.0, 0.0),
        polyline = ""
    )
}
