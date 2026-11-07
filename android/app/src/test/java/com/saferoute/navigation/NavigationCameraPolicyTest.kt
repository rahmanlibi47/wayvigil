package com.saferoute.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationCameraPolicyTest {
    @Test
    fun `navigation follow is the only camera owner during active navigation`() {
        assertEquals(
            NavigationCameraOwner.NAVIGATION_FOLLOW,
            NavigationCameraPolicy.resolveOwner(
                isNavigationActive = true,
                hasArrived = false,
                mode = NavigationCameraMode.FOLLOWING,
                hasRoutePreview = true
            )
        )
    }

    @Test
    fun `free explore owns the camera after user leaves follow mode`() {
        assertEquals(
            NavigationCameraOwner.FREE_EXPLORE,
            NavigationCameraPolicy.resolveOwner(
                isNavigationActive = true,
                hasArrived = false,
                mode = NavigationCameraMode.FREE_EXPLORE,
                hasRoutePreview = true
            )
        )
    }

    @Test
    fun `route overview owns the camera only before active navigation`() {
        assertEquals(
            NavigationCameraOwner.ROUTE_OVERVIEW,
            NavigationCameraPolicy.resolveOwner(
                isNavigationActive = false,
                hasArrived = false,
                mode = NavigationCameraMode.OVERVIEW,
                hasRoutePreview = true
            )
        )
        assertEquals(
            NavigationCameraOwner.NONE,
            NavigationCameraPolicy.resolveOwner(
                isNavigationActive = false,
                hasArrived = true,
                mode = NavigationCameraMode.OVERVIEW,
                hasRoutePreview = true
            )
        )
    }

    @Test
    fun `arrival releases the camera even if navigation state is active`() {
        assertEquals(
            NavigationCameraOwner.NONE,
            NavigationCameraPolicy.resolveOwner(
                isNavigationActive = true,
                hasArrived = true,
                mode = NavigationCameraMode.FOLLOWING,
                hasRoutePreview = true
            )
        )
    }

    @Test
    fun `navigation mode transitions preserve free exploration until recenter`() {
        assertEquals(
            NavigationCameraMode.FOLLOWING,
            NavigationCameraPolicy.modeAfterNavigationChange(
                isNavigationActive = true,
                currentMode = NavigationCameraMode.OVERVIEW
            )
        )
        assertEquals(
            NavigationCameraMode.FREE_EXPLORE,
            NavigationCameraPolicy.modeAfterNavigationChange(
                isNavigationActive = true,
                currentMode = NavigationCameraMode.FREE_EXPLORE
            )
        )
        assertEquals(
            NavigationCameraMode.OVERVIEW,
            NavigationCameraPolicy.modeAfterNavigationChange(
                isNavigationActive = false,
                currentMode = NavigationCameraMode.FOLLOWING
            )
        )
    }

    @Test
    fun `recenter selects follow mode only during active navigation`() {
        assertEquals(
            NavigationCameraMode.FOLLOWING,
            NavigationCameraPolicy.modeAfterRecenter(isNavigationActive = true)
        )
        assertEquals(
            NavigationCameraMode.OVERVIEW,
            NavigationCameraPolicy.modeAfterRecenter(isNavigationActive = false)
        )
    }

    @Test
    fun `follow pauses only for a user camera move away from the follow target`() {
        assertTrue(
            NavigationCameraPolicy.shouldPauseFollow(
                isCameraMoving = true,
                isProgrammaticAnimation = false,
                mode = NavigationCameraMode.FOLLOWING,
                cameraDiffersFromFollowTarget = true
            )
        )
        assertFalse(
            NavigationCameraPolicy.shouldPauseFollow(
                isCameraMoving = true,
                isProgrammaticAnimation = true,
                mode = NavigationCameraMode.FOLLOWING,
                cameraDiffersFromFollowTarget = true
            )
        )
        assertFalse(
            NavigationCameraPolicy.shouldPauseFollow(
                isCameraMoving = true,
                isProgrammaticAnimation = false,
                mode = NavigationCameraMode.FOLLOWING,
                cameraDiffersFromFollowTarget = false
            )
        )
    }

    @Test
    fun `look ahead adapts to screen and zoom while remaining bounded`() {
        val portraitOffset = NavigationCameraPolicy.lookAheadMeters(
            latitude = 12.9,
            screenHeightDp = 850,
            density = 2.75f,
            zoom = 17f
        )
        val landscapeOffset = NavigationCameraPolicy.lookAheadMeters(
            latitude = 12.9,
            screenHeightDp = 400,
            density = 2.75f,
            zoom = 17f
        )
        val highZoomOffset = NavigationCameraPolicy.lookAheadMeters(
            latitude = 12.9,
            screenHeightDp = 850,
            density = 2.75f,
            zoom = 20f
        )

        assertTrue(portraitOffset > landscapeOffset)
        assertTrue(highZoomOffset < portraitOffset)
        assertTrue(portraitOffset in 120.0..450.0)
        assertTrue(landscapeOffset in 120.0..450.0)
    }
}
