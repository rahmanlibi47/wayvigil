package com.saferoute.ui.viewmodel

import android.app.Application
import com.saferoute.data.model.Location
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.NavigationState
import com.saferoute.data.model.Route
import com.saferoute.data.remote.ComputeRouteRequest
import com.saferoute.data.remote.CoordinateDto
import com.saferoute.data.remote.NavigationRouteResponse
import com.saferoute.data.remote.NavigationRouteStepResponse
import com.saferoute.data.remote.NavigationRoutesResponse
import com.saferoute.data.remote.SafeRouteApi
import com.saferoute.navigation.NavigationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationRerouteTest {
    private val navigationManager = mockk<NavigationManager>(relaxed = true)
    private val safeRouteApi = mockk<SafeRouteApi>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `reroute uses the latest location and original destination then resumes navigation`() =
        runTest {
            val origin = LocationData(
                latitude = 12.0,
                longitude = 77.0,
                accuracy = 5f,
                timestamp = System.currentTimeMillis()
            )
            val destination = Location(10.0, 76.0)
            val originalRoute = route(destination)
            configureManager(
                NavigationState.Deviation(
                    lastKnownRoute = originalRoute,
                    currentStep = 1,
                    distanceToNextManeuver = 200,
                    remainingDistance = 2_000,
                    remainingDuration = 300
                ),
                origin
            )
            every { navigationManager.startNavigation(any()) } returns true
            coEvery { safeRouteApi.calculateRoute(any()) } returns
                NavigationRoutesResponse(listOf(replacementResponse()))

            val viewModel = createViewModel()
            viewModel.requestReroute()
            advanceUntilIdle()

            val request = slot<ComputeRouteRequest>()
            coVerify(exactly = 1) { safeRouteApi.calculateRoute(capture(request)) }
            assertEquals(CoordinateDto(12.0, 77.0), request.captured.origin)
            assertEquals(CoordinateDto(10.0, 76.0), request.captured.destination)
            assertEquals("DRIVE", request.captured.travelMode)
            assertFalse(request.captured.computeAlternatives)

            val replacement = slot<Route>()
            verify(exactly = 1) { navigationManager.startNavigation(capture(replacement)) }
            assertEquals("Palakkad", replacement.captured.destinationName)
            assertEquals(destination, replacement.captured.destinationLocation)
            assertEquals(replacement.captured, viewModel.currentRoute.value)
            assertFalse(viewModel.isRerouting.value)
            assertTrue(viewModel.navigationError.value == null)
        }

    @Test
    fun `reroute does not call provider with a stale location`() = runTest {
        val staleLocation = LocationData(
            latitude = 12.0,
            longitude = 77.0,
            accuracy = 5f,
            timestamp = System.currentTimeMillis() - 6_000
        )
        configureManager(
            NavigationState.Deviation(
                lastKnownRoute = route(Location(10.0, 76.0)),
                currentStep = 1,
                distanceToNextManeuver = 200,
                remainingDistance = 2_000,
                remainingDuration = 300
            ),
            staleLocation
        )

        val viewModel = createViewModel()
        viewModel.requestReroute()

        coVerify(exactly = 0) { safeRouteApi.calculateRoute(any()) }
        assertTrue(viewModel.navigationError.value?.contains("fresh GPS") == true)
        assertFalse(viewModel.isRerouting.value)
    }

    @Test
    fun `provider failure leaves navigation in deviation state and allows retry`() = runTest {
        val location = LocationData(
            latitude = 12.0,
            longitude = 77.0,
            accuracy = 5f,
            timestamp = System.currentTimeMillis()
        )
        val deviation = NavigationState.Deviation(
            lastKnownRoute = route(Location(10.0, 76.0)),
            currentStep = 1,
            distanceToNextManeuver = 200,
            remainingDistance = 2_000,
            remainingDuration = 300
        )
        configureManager(deviation, location)
        coEvery { safeRouteApi.calculateRoute(any()) } throws IllegalStateException("provider")

        val viewModel = createViewModel()
        viewModel.requestReroute()
        advanceUntilIdle()

        assertEquals(deviation, viewModel.navigationState.value)
        assertTrue(viewModel.navigationError.value?.contains("Could not find a new route") == true)
        assertFalse(viewModel.isRerouting.value)
        verify(exactly = 0) { navigationManager.startNavigation(any()) }
    }

    private fun configureManager(state: NavigationState, location: LocationData) {
        every { navigationManager.navigationState } returns MutableStateFlow(state)
        every { navigationManager.currentLocation } returns MutableStateFlow(location)
        every { navigationManager.currentSpeed } returns MutableStateFlow(0f)
        every { navigationManager.heading } returns MutableStateFlow(null)
        every { navigationManager.locationQuality } returns
            MutableStateFlow(NavigationManager.LocationQuality.GOOD)
        every { navigationManager.hasLocationPermission() } returns true
    }

    private fun createViewModel() = NavigationViewModel(
        application = mockk<Application>(relaxed = true),
        navigationManager = navigationManager,
        safeRouteApi = safeRouteApi
    )

    private fun route(destination: Location) = Route(
        polyline = "old-route",
        distanceMeters = 3_000,
        durationSeconds = 500,
        steps = emptyList(),
        destinationName = "Palakkad",
        destinationLocation = destination
    )

    private fun replacementResponse() = NavigationRouteResponse(
        polyline = "new-route",
        distanceMeters = 2_500,
        durationSeconds = 400,
        steps = listOf(
            NavigationRouteStepResponse(
                instruction = "Continue",
                distanceMeters = 2_500,
                durationSeconds = 400,
                maneuver = "DEPART",
                startLocation = CoordinateDto(12.0, 77.0),
                endLocation = CoordinateDto(10.0, 76.0)
            )
        )
    )
}
