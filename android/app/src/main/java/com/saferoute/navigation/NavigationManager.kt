package com.saferoute.navigation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.location.Location
import android.os.IBinder
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.saferoute.data.model.LocationData
import com.saferoute.data.model.NavigationState
import com.saferoute.data.model.Route
import com.saferoute.service.LocationTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central manager for navigation state and location updates.
 * Coordinates between GPS, route matching, and UI state.
 */
@Singleton
class NavigationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fusedLocationClient: FusedLocationProviderClient
) {
    // State flows
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    // Navigation logic components
    private val speedCalculator = SpeedCalculator()
    private val routeMatcher = RouteMatcher()

    // Location tracking
    private var locationCallback: LocationCallback? = null
    private var isTracking = false

    // Service binding
    private var locationService: LocationTrackingService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as LocationTrackingService.LocalBinder
            locationService = binder.getService()
            serviceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            locationService = null
            serviceBound = false
        }
    }

    /**
     * Starts location tracking with appropriate permissions
     */
    fun startLocationTracking(): Boolean {
        if (!hasLocationPermission()) {
            return false
        }

        if (isTracking) {
            return true
        }

        // Start and bind to foreground service
        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
        context.bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)

        // Also request direct location updates for UI
        startDirectLocationUpdates()

        isTracking = true
        return true
    }

    /**
     * Stops location tracking
     */
    fun stopLocationTracking() {
        if (!isTracking) return

        stopDirectLocationUpdates()

        if (serviceBound) {
            context.unbindService(serviceConnection)
            serviceBound = false
        }

        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        context.stopService(serviceIntent)

        locationService = null
        isTracking = false
    }

    /**
     * Starts navigation with a calculated route
     */
    fun startNavigation(route: Route) {
        _navigationState.value = NavigationState.Navigating(
            route = route,
            currentStep = 0,
            remainingDistance = route.distanceMeters,
            remainingDuration = route.durationSeconds
        )
        routeMatcher.reset()
    }

    /**
     * Stops navigation
     */
    fun stopNavigation() {
        _navigationState.value = NavigationState.Idle
        routeMatcher.reset()
    }

    /**
     * Updates current location and checks route progress
     */
    private fun updateLocation(location: Location) {
        val locationData = LocationData(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            altitude = location.altitude,
            speed = location.speed,
            speedAccuracy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                location.speedAccuracyMetersPerSecond
            } else {
                0f
            },
            bearing = location.bearing,
            bearingAccuracy = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                location.bearingAccuracyDegrees
            } else {
                0f
            },
            timestamp = location.time
        )

        _currentLocation.value = locationData
        _heading.value = if (location.hasBearing()) location.bearing else null

        // Update speed with filtering
        val filteredSpeed = speedCalculator.addLocation(locationData)
        _currentSpeed.value = filteredSpeed

        // Check route progress if navigating
        checkRouteProgress(locationData)
    }

    /**
     * Checks if user is on route and updates state accordingly
     */
    private fun checkRouteProgress(locationData: LocationData) {
        val currentState = _navigationState.value
        if (currentState !is NavigationState.Navigating) return

        val matchResult = routeMatcher.matchLocationToRoute(
            locationData,
            currentState.route,
            _heading.value
        )

        when (matchResult) {
            is RouteMatcher.MatchResult.OnRoute -> {
                // Update progress
                val remainingDistance = (currentState.route.distanceMeters * (1 - matchResult.progressFraction)).toInt()
                val remainingDuration = (currentState.route.durationSeconds * (1 - matchResult.progressFraction)).toInt()

                _navigationState.value = currentState.copy(
                    remainingDistance = remainingDistance,
                    remainingDuration = remainingDuration
                )

                // Check if arrived
                if (remainingDistance < ARRIVAL_THRESHOLD_METERS) {
                    _navigationState.value = NavigationState.Arrived(
                        destination = "Destination" // TODO: Get from route
                    )
                }
            }
            is RouteMatcher.MatchResult.OffRoute -> {
                when (matchResult.suggestedAction) {
                    RouteMatcher.SuggestedAction.REROUTE -> {
                        _navigationState.value = NavigationState.Deviation(currentState.route)
                    }
                    else -> {
                        // Continue for now, may be temporary
                    }
                }
            }
        }
    }

    /**
     * Requests rerouting when user is off route
     */
    fun requestReroute() {
        val currentState = _navigationState.value
        if (currentState is NavigationState.Deviation) {
            _navigationState.value = NavigationState.Rerouting(currentState.lastKnownRoute)
        }
    }

    /**
     * Starts direct location updates for UI
     */
    private fun startDirectLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_MS)
            setWaitForAccurateLocation(true)
        }.build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    updateLocation(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            // Permission not granted
            isTracking = false
        }
    }

    /**
     * Stops direct location updates
     */
    private fun stopDirectLocationUpdates() {
        locationCallback?.let { callback ->
            fusedLocationClient.removeLocationUpdates(callback)
        }
        locationCallback = null
    }

    /**
     * Checks if location permission is granted
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Gets the last known location if available
     */
    suspend fun getLastLocation(): LatLng? {
        return try {
            val location = fusedLocationClient.lastLocation.await()
            location?.let { LatLng(it.latitude, it.longitude) }
        } catch (e: SecurityException) {
            null
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 1000L
        private const val FASTEST_UPDATE_INTERVAL_MS = 500L
        private const val ARRIVAL_THRESHOLD_METERS = 50
    }
}
