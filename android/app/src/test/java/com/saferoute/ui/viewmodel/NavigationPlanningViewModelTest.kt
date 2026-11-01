package com.saferoute.ui.viewmodel

import android.app.Application
import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.NavigationTravelMode
import com.saferoute.data.remote.ComputeRouteRequest
import com.saferoute.data.remote.NavigationRouteResponse
import com.saferoute.data.remote.NavigationRoutesResponse
import com.saferoute.data.remote.PlaceDetailsResponse
import com.saferoute.data.remote.SafeRouteApi
import com.saferoute.navigation.NavigationManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NavigationPlanningViewModelTest {
    private val navigationManager = mockk<NavigationManager>(relaxed = true)
    private val safeRouteApi = mockk<SafeRouteApi>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { navigationManager.navigationState } returns
            MutableStateFlow(com.saferoute.data.model.NavigationState.Idle)
        every { navigationManager.currentLocation } returns
            MutableStateFlow<com.saferoute.data.model.LocationData?>(null)
        every { navigationManager.currentSpeed } returns MutableStateFlow(0f)
        every { navigationManager.heading } returns MutableStateFlow(null)
        every { navigationManager.locationQuality } returns
            MutableStateFlow(NavigationManager.LocationQuality.SEARCHING)
        every { navigationManager.hasLocationPermission() } returns true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `driving route request offers alternatives and selected option becomes preview`() = runTest {
        coEvery { safeRouteApi.getPlaceDetails("place-1", any()) } returns placeDetails()
        coEvery { safeRouteApi.calculateRoute(any()) } returns NavigationRoutesResponse(
            listOf(
                routeResponse("primary", "DEFAULT_ROUTE", 1_000),
                routeResponse("alternate", "DEFAULT_ROUTE_ALTERNATE", 1_200)
            )
        )
        val viewModel = createViewModel()
        viewModel.selectDestination(
            NavigationViewModel.SearchResult("place-1", "Palakkad", "Kerala")
        )
        advanceUntilIdle()

        viewModel.calculateRoute(LatLng(12.0, 77.0))
        advanceUntilIdle()

        val request = slot<ComputeRouteRequest>()
        coVerify(exactly = 1) { safeRouteApi.calculateRoute(capture(request)) }
        assertEquals("DRIVE", request.captured.travelMode)
        assertTrue(request.captured.computeAlternatives)
        assertEquals(2, viewModel.routeOptions.value.size)
        viewModel.selectRoute(1)
        assertEquals("alternate", viewModel.routePreview.value?.polyline)
        assertEquals("Palakkad", viewModel.routePreview.value?.destinationName)
    }

    @Test
    fun `two-wheeler request uses motorcycle mode and preserves provider safety warning`() = runTest {
        coEvery { safeRouteApi.getPlaceDetails("place-1", any()) } returns placeDetails()
        coEvery { safeRouteApi.calculateRoute(any()) } returns NavigationRoutesResponse(
            listOf(
                routeResponse(
                    polyline = "motorcycle-route",
                    label = "DEFAULT_ROUTE",
                    distanceMeters = 900,
                    warnings = listOf("Two-wheeler route coverage warning")
                )
            )
        )
        val viewModel = createViewModel()
        viewModel.selectDestination(
            NavigationViewModel.SearchResult("place-1", "Palakkad", "Kerala")
        )
        advanceUntilIdle()

        viewModel.setTravelMode(NavigationTravelMode.TWO_WHEELER)
        viewModel.calculateRoute(LatLng(12.0, 77.0))
        advanceUntilIdle()

        val request = slot<ComputeRouteRequest>()
        coVerify(exactly = 1) { safeRouteApi.calculateRoute(capture(request)) }
        assertEquals("TWO_WHEELER", request.captured.travelMode)
        assertTrue(request.captured.computeAlternatives)
        assertEquals(NavigationTravelMode.TWO_WHEELER, viewModel.routePreview.value?.travelMode)
        assertEquals(
            listOf("Two-wheeler route coverage warning"),
            viewModel.routePreview.value?.warnings
        )
    }

    private fun createViewModel() = NavigationViewModel(
        application = mockk<Application>(relaxed = true),
        navigationManager = navigationManager,
        safeRouteApi = safeRouteApi
    )

    private fun placeDetails() = PlaceDetailsResponse(
        placeId = "place-1",
        name = "Palakkad",
        address = "Kerala, India",
        latitude = 10.77,
        longitude = 76.65
    )

    private fun routeResponse(
        polyline: String,
        label: String,
        distanceMeters: Int,
        warnings: List<String> = emptyList()
    ) = NavigationRouteResponse(
        routeLabel = label,
        polyline = polyline,
        distanceMeters = distanceMeters,
        durationSeconds = 300,
        steps = emptyList(),
        warnings = warnings
    )
}
