package com.saferoute.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.Location
import com.saferoute.data.model.NavigationState
import com.saferoute.data.model.NavigationTravelMode
import com.saferoute.data.model.Route
import com.saferoute.data.remote.ComputeRouteRequest
import com.saferoute.data.remote.CoordinateDto
import com.saferoute.data.remote.PlaceDetailsResponse
import com.saferoute.data.remote.SafeRouteApi
import com.saferoute.navigation.ManeuverAnnouncementTracker
import com.saferoute.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for navigation-related UI state.
 * Bridges between NavigationManager and Compose UI.
 */
@HiltViewModel
class NavigationViewModel @Inject constructor(
    application: Application,
    private val navigationManager: NavigationManager,
    private val safeRouteApi: SafeRouteApi
) : AndroidViewModel(application) {

    // Expose navigation manager flows
    val navigationState: StateFlow<NavigationState> = navigationManager.navigationState
    val currentLocation: StateFlow<com.saferoute.data.model.LocationData?> =
        navigationManager.currentLocation
    val currentSpeed: StateFlow<Float> = navigationManager.currentSpeed
    val heading: StateFlow<Float?> = navigationManager.heading
    val locationQuality: StateFlow<NavigationManager.LocationQuality> =
        navigationManager.locationQuality

    // UI state
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _hasSearchCompleted = MutableStateFlow(false)
    val hasSearchCompleted: StateFlow<Boolean> = _hasSearchCompleted.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _selectedDestination = MutableStateFlow<SearchResult?>(null)
    val selectedDestination: StateFlow<SearchResult?> = _selectedDestination.asStateFlow()

    private val _destinationDetails = MutableStateFlow<PlaceDetailsResponse?>(null)
    val destinationDetails = _destinationDetails.asStateFlow()

    private val _isLoadingPlaceDetails = MutableStateFlow(false)
    val isLoadingPlaceDetails = _isLoadingPlaceDetails.asStateFlow()

    private val _routePreview = MutableStateFlow<Route?>(null)
    val routePreview = _routePreview.asStateFlow()

    private val _routeOptions = MutableStateFlow<List<Route>>(emptyList())
    val routeOptions = _routeOptions.asStateFlow()

    private val _selectedRouteIndex = MutableStateFlow(0)
    val selectedRouteIndex = _selectedRouteIndex.asStateFlow()

    private val _travelMode = MutableStateFlow(NavigationTravelMode.DRIVE)
    val travelMode = _travelMode.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute = _isCalculatingRoute.asStateFlow()

    private val _navigationError = MutableStateFlow<String?>(null)
    val navigationError = _navigationError.asStateFlow()

    private val _isRerouting = MutableStateFlow(false)
    val isRerouting = _isRerouting.asStateFlow()

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()

    private var searchJob: Job? = null
    private var detailsJob: Job? = null
    private var routeJob: Job? = null
    private var rerouteJob: Job? = null
    private var placesSessionToken: String = UUID.randomUUID().toString()
    private val maneuverAnnouncementTracker = ManeuverAnnouncementTracker()
    private var announcementRoutePolyline: String? = null
    private var searchLatitude: Double? = null
    private var searchLongitude: Double? = null

    init {
        checkPermissionStatus()
    }

    /**
     * Checks if location permission is granted
     */
    private fun checkPermissionStatus() {
        _permissionGranted.value = navigationManager.hasLocationPermission()
    }

    /**
     * Updates permission status (call after permission request)
     */
    fun onPermissionResult(granted: Boolean) {
        _permissionGranted.value = granted
    }

    /**
     * Updates search query
     */
    fun onSearchQueryChange(
        query: String,
        latitude: Double? = null,
        longitude: Double? = null
    ) {
        if (_searchQuery.value != query && _selectedDestination.value != null) {
            detailsJob?.cancel()
            routeJob?.cancel()
            _selectedDestination.value = null
            _destinationDetails.value = null
            _routePreview.value = null
            _routeOptions.value = emptyList()
            _searchError.value = null
            _navigationError.value = null
            _isLoadingPlaceDetails.value = false
            _isCalculatingRoute.value = false
        }
        _searchQuery.value = query
        _searchError.value = null
        searchLatitude = latitude
        searchLongitude = longitude
        searchJob?.cancel()
        if (query.length >= 3) {
            _hasSearchCompleted.value = false
            performSearch()
        } else {
            _searchResults.value = emptyList()
            _searchError.value = null
            _isSearching.value = false
            _hasSearchCompleted.value = false
        }
    }

    /**
     * Performs place search
     */
    private fun performSearch() {
        val query = _searchQuery.value
        searchJob = viewModelScope.launch {
            delay(300)
            _isSearching.value = true
            _searchError.value = null
            try {
                val response = safeRouteApi.autocompletePlaces(
                    query,
                    placesSessionToken,
                    searchLatitude,
                    searchLongitude
                )
                if (_searchQuery.value == query) {
                    _searchResults.value = response.map {
                        SearchResult(it.placeId, it.name, it.address)
                    }
                    _hasSearchCompleted.value = true
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_searchQuery.value == query) {
                    _searchResults.value = emptyList()
                    _searchError.value = "Place search is temporarily unavailable. Try again."
                    _hasSearchCompleted.value = true
                }
            } finally {
                if (_searchQuery.value == query) _isSearching.value = false
            }
        }
    }

    /**
     * Selects a destination for navigation
     */
    fun selectDestination(result: SearchResult) {
        searchJob?.cancel()
        detailsJob?.cancel()
        routeJob?.cancel()
        _selectedDestination.value = result
        _destinationDetails.value = null
        _routePreview.value = null
        _routeOptions.value = emptyList()
        _selectedRouteIndex.value = 0
        _isCalculatingRoute.value = false
        _searchResults.value = emptyList()
        _isSearching.value = false
        _hasSearchCompleted.value = false
        _searchQuery.value = result.name
        val sessionToken = placesSessionToken
        placesSessionToken = UUID.randomUUID().toString()
        _searchError.value = null
        _navigationError.value = null
        detailsJob = viewModelScope.launch {
            _isLoadingPlaceDetails.value = true
            try {
                val details = safeRouteApi.getPlaceDetails(result.id, sessionToken)
                if (_selectedDestination.value?.id == result.id) {
                    _destinationDetails.value = details
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_selectedDestination.value?.id == result.id) {
                    _searchError.value = "Could not load this place. Choose it again to retry."
                }
            } finally {
                if (_selectedDestination.value?.id == result.id) {
                    _isLoadingPlaceDetails.value = false
                }
            }
        }
    }

    /**
     * Clears selected destination
     */
    fun clearDestination() {
        detailsJob?.cancel()
        routeJob?.cancel()
        _selectedDestination.value = null
        _destinationDetails.value = null
        _searchQuery.value = ""
        _currentRoute.value = null
        _routePreview.value = null
        _routeOptions.value = emptyList()
        _selectedRouteIndex.value = 0
        _navigationError.value = null
        _searchError.value = null
        _isLoadingPlaceDetails.value = false
        _hasSearchCompleted.value = false
    }

    /**
     * Calculates route to selected destination
     */
    fun setTravelMode(mode: NavigationTravelMode) {
        if (_travelMode.value == mode) return
        _travelMode.value = mode
        routeJob?.cancel()
        _routePreview.value = null
        _routeOptions.value = emptyList()
        _selectedRouteIndex.value = 0
        _isCalculatingRoute.value = false
        _navigationError.value = null
    }

    fun selectRoute(index: Int) {
        val options = _routeOptions.value
        if (index !in options.indices) return
        _selectedRouteIndex.value = index
        _routePreview.value = options[index]
    }

    fun calculateRoute(origin: LatLng?) {
        val destination = _destinationDetails.value ?: return
        if (origin == null) {
            _navigationError.value = "Your current location is not available yet."
            return
        }
        routeJob?.cancel()
        _navigationError.value = null
        routeJob = viewModelScope.launch {
            _isCalculatingRoute.value = true
            try {
                val response = safeRouteApi.calculateRoute(
                    ComputeRouteRequest(
                        origin = CoordinateDto(origin.latitude, origin.longitude),
                        destination = CoordinateDto(destination.latitude, destination.longitude),
                        travelMode = _travelMode.value.name,
                        computeAlternatives = true
                    )
                )
                val options = response.routes.map { route ->
                    route.toDomainRoute().copy(
                        travelMode = _travelMode.value,
                        destinationName = destination.name,
                        destinationLocation = Location(destination.latitude, destination.longitude)
                    )
                }
                if (options.isEmpty()) {
                    _navigationError.value = "No route is available for this destination."
                } else {
                    _routeOptions.value = options
                    _selectedRouteIndex.value = 0
                    _routePreview.value = options.first()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _navigationError.value = "Could not calculate a route. Check your connection and try again."
            } finally {
                _isCalculatingRoute.value = false
            }
        }
    }

    /**
     * Starts navigation with calculated route
     */
    fun startNavigation(route: Route): Boolean {
        _navigationError.value = null
        if (!navigationManager.startNavigation(route)) {
            _navigationError.value = "Precise location permission is required to start navigation."
            return false
        }
        resetManeuverAnnouncements()
        _currentRoute.value = route
        return true
    }

    /**
     * Stops navigation
     */
    fun stopNavigation() {
        rerouteJob?.cancel()
        _isRerouting.value = false
        _navigationError.value = null
        navigationManager.stopNavigation()
        resetManeuverAnnouncements()
        _currentRoute.value = null
        _routePreview.value = null
        _routeOptions.value = emptyList()
        _selectedRouteIndex.value = 0
    }

    fun continueNavigation() {
        rerouteJob?.cancel()
        _isRerouting.value = false
        _navigationError.value = null
        navigationManager.continueNavigation()
    }

    fun nextManeuverAnnouncement(): String? {
        val state = navigationState.value as? NavigationState.Navigating ?: return null
        if (announcementRoutePolyline != state.route.polyline) {
            maneuverAnnouncementTracker.reset()
            announcementRoutePolyline = state.route.polyline
        }
        return maneuverAnnouncementTracker.nextAnnouncement(
            currentStep = state.currentStep,
            distanceToNextManeuverMeters = state.distanceToNextManeuver,
            steps = state.route.steps
        )
    }

    fun resetManeuverAnnouncements() {
        maneuverAnnouncementTracker.reset()
        announcementRoutePolyline = null
    }

    fun requestReroute() {
        if (_isRerouting.value) return
        val deviation = navigationState.value as? NavigationState.Deviation ?: return
        val location = currentLocation.value
        if (location == null || locationQuality.value != NavigationManager.LocationQuality.GOOD ||
            System.currentTimeMillis() - location.timestamp !in 0..MAX_LOCATION_AGE_FOR_REROUTE_MS
        ) {
            _navigationError.value = "Waiting for a fresh GPS location. Try again shortly."
            return
        }

        val destination = deviation.lastKnownRoute.destinationLocation
            ?: deviation.lastKnownRoute.steps.lastOrNull()?.endLocation
            ?: _destinationDetails.value?.let { Location(it.latitude, it.longitude) }
        if (destination == null) {
            _navigationError.value =
                "This route is missing its destination location. End navigation and select it again."
            return
        }

        _navigationError.value = null
        _isRerouting.value = true
        rerouteJob = viewModelScope.launch {
            try {
                val response = safeRouteApi.calculateRoute(
                    ComputeRouteRequest(
                        origin = CoordinateDto(location.latitude, location.longitude),
                        destination = CoordinateDto(destination.latitude, destination.longitude),
                        travelMode = deviation.lastKnownRoute.travelMode.name,
                        computeAlternatives = false
                    )
                )
                val replacementRoute = response.routes.firstOrNull()?.toDomainRoute()?.copy(
                    travelMode = deviation.lastKnownRoute.travelMode,
                    destinationName = deviation.lastKnownRoute.destinationName,
                    destinationLocation = destination
                ) ?: throw IllegalStateException("No replacement route returned")
                if (navigationState.value !is NavigationState.Deviation ||
                    !navigationManager.startNavigation(replacementRoute)
                ) {
                    _navigationError.value = "Could not resume navigation with the new route."
                    return@launch
                }
                _currentRoute.value = replacementRoute
                _routePreview.value = replacementRoute
                resetManeuverAnnouncements()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _navigationError.value = "Could not find a new route. Check your connection and try again."
            } finally {
                _isRerouting.value = false
            }
        }
    }

    override fun onCleared() {
        rerouteJob?.cancel()
        _isRerouting.value = false
        super.onCleared()
        navigationManager.stopNavigation()
    }

    data class SearchResult(
        val id: String,
        val name: String,
        val address: String
    )

    companion object {
        private const val MAX_LOCATION_AGE_FOR_REROUTE_MS = 5_000L
    }
}
