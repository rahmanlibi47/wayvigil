package com.saferoute.navigation

import kotlin.math.cos
import kotlin.math.pow

internal enum class NavigationCameraMode {
    OVERVIEW,
    FOLLOWING,
    FREE_EXPLORE
}

internal enum class NavigationCameraOwner {
    ROUTE_OVERVIEW,
    NAVIGATION_FOLLOW,
    FREE_EXPLORE,
    NONE
}

internal object NavigationCameraPolicy {
    fun modeAfterNavigationChange(
        isNavigationActive: Boolean,
        currentMode: NavigationCameraMode
    ): NavigationCameraMode = when {
        !isNavigationActive -> NavigationCameraMode.OVERVIEW
        currentMode == NavigationCameraMode.OVERVIEW -> NavigationCameraMode.FOLLOWING
        else -> currentMode
    }

    fun modeAfterRecenter(isNavigationActive: Boolean): NavigationCameraMode =
        if (isNavigationActive) {
            NavigationCameraMode.FOLLOWING
        } else {
            NavigationCameraMode.OVERVIEW
        }

    fun resolveOwner(
        isNavigationActive: Boolean,
        hasArrived: Boolean,
        mode: NavigationCameraMode,
        hasRoutePreview: Boolean
    ): NavigationCameraOwner = when {
        hasArrived -> NavigationCameraOwner.NONE
        isNavigationActive && mode == NavigationCameraMode.FOLLOWING ->
            NavigationCameraOwner.NAVIGATION_FOLLOW
        isNavigationActive && mode == NavigationCameraMode.FREE_EXPLORE ->
            NavigationCameraOwner.FREE_EXPLORE
        isNavigationActive -> NavigationCameraOwner.NONE
        hasRoutePreview -> NavigationCameraOwner.ROUTE_OVERVIEW
        else -> NavigationCameraOwner.NONE
    }

    fun shouldPauseFollow(
        isCameraMoving: Boolean,
        isProgrammaticAnimation: Boolean,
        mode: NavigationCameraMode,
        cameraDiffersFromFollowTarget: Boolean
    ): Boolean = isCameraMoving && !isProgrammaticAnimation &&
        mode == NavigationCameraMode.FOLLOWING && cameraDiffersFromFollowTarget

    fun lookAheadMeters(
        latitude: Double,
        screenHeightDp: Int,
        density: Float,
        zoom: Float
    ): Double {
        val metersPerPixel = METERS_PER_PIXEL_AT_EQUATOR / 2.0.pow(zoom.toDouble()) *
            cos(Math.toRadians(latitude))
        val visibleHeightPixels = screenHeightDp * density
        return (visibleHeightPixels.toDouble() * TARGET_OFFSET_FRACTION * metersPerPixel)
            .coerceIn(MIN_LOOK_AHEAD_METERS, MAX_LOOK_AHEAD_METERS)
    }

    private const val METERS_PER_PIXEL_AT_EQUATOR = 156_543.03392
    private const val TARGET_OFFSET_FRACTION = 0.16
    private const val MIN_LOOK_AHEAD_METERS = 120.0
    private const val MAX_LOOK_AHEAD_METERS = 450.0
}
