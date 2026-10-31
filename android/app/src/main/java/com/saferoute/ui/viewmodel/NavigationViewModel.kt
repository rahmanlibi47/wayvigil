package com.saferoute.ui.viewmodel

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.NavigationState
import com.saferoute.data.model.Route
import com.saferoute.navigation.NavigationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for navigation-related UI state.
 * Bridges between NavigationManager and Compose UI.
 */
@HiltViewModel
class NavigationViewModel @Inject constructor(
    application: Application,
    private val navigationManager: NavigationManager
) : AndroidViewModel(application) {

    // Expose navigation manager flows
    val navigationState: StateFlow<NavigationState> = navigationManager.navigationState
    val currentLocation: StateFlow<com.saferoute.data.model.LocationData?> = navigationManager.currentLocation
    val currentSpeed: StateFlow<Float> = navigationManager.currentSpeed
    val heading: StateFlow<Float?> = navigationManager.heading

    // UI state
    private val _permissionGranted = MutableStateFlow(false)
    val permissionGranted: StateFlow<Boolean> = _permissionGranted.asStateFlow()

    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResult>>(emptyList())
    val searchResults: StateFlow<List<SearchResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _selectedDestination = MutableStateFlow<SearchResult?>(null)
    val selectedDestination: StateFlow<SearchResult?> = _selectedDestination.asStateFlow()

    private val _currentRoute = MutableStateFlow<Route?>(null)
    val currentRoute: StateFlow<Route?> = _currentRoute.asStateFlow()

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
        if (granted) {
            startLocationTracking()
        }
    }

    /**
     * Starts location tracking
     */
    fun startLocationTracking() {
        if (navigationManager.startLocationTracking()) {
            _isTracking.value = true
        }
    }

    /**
     * Stops location tracking
     */
    fun stopLocationTracking() {
        navigationManager.stopLocationTracking()
        _isTracking.value = false
    }

    /**
     * Updates search query
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        if (query.length >= 3) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    /**
     * Performs place search
     */
    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                // TODO: Implement Places API search
                // For now, return empty results
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Selects a destination for navigation
     */
    fun selectDestination(result: SearchResult) {
        _selectedDestination.value = result
        _searchResults.value = emptyList()
        _searchQuery.value = result.name
    }

    /**
     * Clears selected destination
     */
    fun clearDestination() {
        _selectedDestination.value = null
        _searchQuery.value = ""
        _currentRoute.value = null
    }

    /**
     * Calculates route to selected destination
     */
    fun calculateRoute() {
        val destination = _selectedDestination.value ?: return

        viewModelScope.launch {
            // TODO: Call Routes API to calculate route
            // For now, simulate a route calculation
            val currentLocation = navigationManager.getLastLocation()
            if (currentLocation != null) {
                // Mock route for testing
                // TODO: Replace with actual Routes API call
            }
        }
    }

    /**
     * Starts navigation with calculated route
     */
    fun startNavigation(route: Route) {
        _currentRoute.value = route
        navigationManager.startNavigation(route)
    }

    /**
     * Stops navigation
     */
    fun stopNavigation() {
        navigationManager.stopNavigation()
        _currentRoute.value = null
    }

    /**
     * Requests rerouting when off route
     */
    fun requestReroute() {
        navigationManager.requestReroute()
    }

    override fun onCleared() {
        super.onCleared()
        stopLocationTracking()
    }

    data class SearchResult(
        val id: String,
        val name: String,
        val address: String,
        val location: LatLng
    )
}
