package com.saferoute.data.remote

import com.saferoute.data.model.ManeuverType
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationApiModelsTest {

    @Test
    fun `route response maps provider fields to navigation domain model`() {
        val response = NavigationRouteResponse(
            routeLabel = "DEFAULT_ROUTE_ALTERNATE",
            polyline = "route-line",
            distanceMeters = 1200,
            durationSeconds = 240,
            steps = listOf(
                NavigationRouteStepResponse(
                    instruction = "Turn right",
                    distanceMeters = 200,
                    durationSeconds = 40,
                    maneuver = "TURN_RIGHT",
                    startLocation = CoordinateDto(12.97, 77.59),
                    endLocation = CoordinateDto(12.971, 77.591),
                    polyline = "step-line"
                )
            ),
            warnings = listOf("Use caution")
        )

        val route = response.toDomainRoute()

        assertEquals("route-line", route.polyline)
        assertEquals("DEFAULT_ROUTE_ALTERNATE", route.routeLabel)
        assertEquals(listOf("Use caution"), route.warnings)
        assertEquals(1200, route.distanceMeters)
        assertEquals(240, route.durationSeconds)
        assertEquals(ManeuverType.TURN_RIGHT, route.steps.single().maneuver)
        assertEquals(12.97, route.steps.single().startLocation.latitude, 0.0001)
    }

    @Test
    fun `routes response preserves all provider alternatives`() {
        val routes = NavigationRoutesResponse(
            listOf(
                NavigationRouteResponse(
                    polyline = "primary",
                    distanceMeters = 1000,
                    durationSeconds = 120,
                    steps = emptyList()
                ),
                NavigationRouteResponse(
                    routeLabel = "DEFAULT_ROUTE_ALTERNATE",
                    polyline = "alternate",
                    distanceMeters = 1200,
                    durationSeconds = 100,
                    steps = emptyList()
                )
            )
        )

        assertEquals(2, routes.routes.size)
        assertEquals("alternate", routes.routes[1].polyline)
    }
}
