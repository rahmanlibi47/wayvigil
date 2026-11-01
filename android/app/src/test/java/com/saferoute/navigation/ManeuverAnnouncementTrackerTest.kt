package com.saferoute.navigation

import com.saferoute.data.model.Location
import com.saferoute.data.model.ManeuverType
import com.saferoute.data.model.RouteStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManeuverAnnouncementTrackerTest {

    @Test
    fun `announces current step once then announces approach and immediate maneuver`() {
        val tracker = ManeuverAnnouncementTracker()
        val steps = listOf(
            createStep("Head north"),
            createStep("Turn left"),
            createStep("Arrive at destination")
        )

        assertEquals("Head north", tracker.nextAnnouncement(0, 500, steps))
        assertEquals("In 300 meters, Turn left", tracker.nextAnnouncement(0, 280, steps))
        assertNull(tracker.nextAnnouncement(0, 250, steps))
        assertEquals("Now, Turn left", tracker.nextAnnouncement(0, 45, steps))
        assertNull(tracker.nextAnnouncement(0, 40, steps))
        assertNull(tracker.nextAnnouncement(1, 500, steps))
    }

    @Test
    fun `announces a maneuver immediately if gps progress skips its approach window`() {
        val tracker = ManeuverAnnouncementTracker()
        val steps = listOf(createStep("Head north"), createStep("Turn right"))

        tracker.nextAnnouncement(0, 500, steps)
        assertEquals("Now, Turn right", tracker.nextAnnouncement(1, 0, steps))
    }

    private fun createStep(instruction: String) = RouteStep(
        instruction = instruction,
        distanceMeters = 500,
        durationSeconds = 60,
        maneuver = ManeuverType.STRAIGHT,
        startLocation = Location(0.0, 0.0),
        endLocation = Location(0.0, 0.0),
        polyline = ""
    )
}
