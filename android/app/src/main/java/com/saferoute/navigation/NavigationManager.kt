package com.saferoute.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
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
    enum class LocationQuality { SEARCHING, GOOD, POOR, STALE }

    // State flows
    private val _navigationState = MutableStateFlow<NavigationState>(NavigationState.Idle)
    val navigationState: StateFlow<NavigationState> = _navigationState.asStateFlow()

    private val _currentLocation = MutableStateFlow<LocationData?>(null)
    val currentLocation: StateFlow<LocationData?> = _currentLocation.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _heading = MutableStateFlow<Float?>(null)
    val heading: StateFlow<Float?> = _heading.asStateFlow()

    private val _locationQuality = MutableStateFlow(LocationQuality.SEARCHING)
    val locationQuality: StateFlow<LocationQuality> = _locationQuality.asStateFlow()

    // Navigation logic components
    private val speedCalculator = SpeedCalculator()
    private val routeMatcher = RouteMatcher()
    private val offRouteDetector = OffRouteDetector()
    private var routeProgressTracker: RouteProgressTracker? = null

    // Location tracking
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    private var trackingStartedAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val staleLocationCheck = object : Runnable {
        override fun run() {
            if (!isTracking) return
            val lastFix = _currentLocation.value
            val lastUpdateTime = lastFix?.timestamp ?: trackingStartedAt
            if (System.currentTimeMillis() - lastUpdateTime > LOCATION_STALE_AFTER_MS) {
                _locationQuality.value = LocationQuality.STALE
                _currentSpeed.value = speedCalculator.getFilteredSpeed()
            }
            mainHandler.postDelayed(this, LOCATION_QUALITY_CHECK_INTERVAL_MS)
        }
    }

    /**
     * Starts location tracking with appropriate permissions
     */
    private fun startLocationTracking(): Boolean {
        if (!hasLocationPermission()) {
            return false
        }

        if (isTracking) {
            return true
        }

        // The service owns the notification; NavigationManager is the sole location source.
        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        return try {
            ContextCompat.startForegroundService(context, serviceIntent)
            isTracking = true
            trackingStartedAt = System.currentTimeMillis()
            _locationQuality.value = LocationQuality.SEARCHING
            mainHandler.removeCallbacks(staleLocationCheck)
            mainHandler.postDelayed(staleLocationCheck, LOCATION_QUALITY_CHECK_INTERVAL_MS)
            startDirectLocationUpdates()
        } catch (e: SecurityException) {
            isTracking = false
            context.stopService(serviceIntent)
            false
        }
    }

    /**
     * Stops location tracking
     */
    private fun stopLocationTracking() {
        mainHandler.removeCallbacks(staleLocationCheck)
        if (!isTracking) return

        stopDirectLocationUpdates()

        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        context.stopService(serviceIntent)

        isTracking = false
        _locationQuality.value = LocationQuality.SEARCHING
        speedCalculator.clear()
        _currentSpeed.value = 0f
    }

    /**
     * Starts navigation with a calculated route
     */
    fun startNavigation(route: Route): Boolean {
        if (!startLocationTracking()) return false
        val current = _navigationState.value
        if (current is NavigationState.Navigating && current.route == route) return true
        _navigationState.value = NavigationState.Navigating(
            route = route,
            currentStep = 0,
            distanceToNextManeuver = route.steps.firstOrNull()?.distanceMeters ?: 0,
            remainingDistance = route.distanceMeters,
            remainingDuration = route.durationSeconds
        )
        routeMatcher.reset()
        offRouteDetector.reset()
        routeProgressTracker = RouteProgressTracker(route)
        return true
    }

    /**
     * Stops navigation
     */
    fun stopNavigation() {
        _navigationState.value = NavigationState.Idle
        routeMatcher.reset()
        offRouteDetector.reset()
        routeProgressTracker = null
        stopLocationTracking()
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

        val ageMs = System.currentTimeMillis() - locationData.timestamp
        if (!locationData.latitude.isFinite() || !locationData.longitude.isFinite() ||
            locationData.latitude !in -90.0..90.0 || locationData.longitude !in -180.0..180.0 ||
            !locationData.hasAcceptableAccuracy() || ageMs < -FUTURE_TIMESTAMP_TOLERANCE_MS ||
            ageMs > LOCATION_STALE_AFTER_MS
        ) {
            _locationQuality.value = LocationQuality.POOR
            return
        }

        _locationQuality.value = LocationQuality.GOOD
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
        val previousState = _navigationState.value
        val currentState = when (previousState) {
            is NavigationState.Navigating -> previousState
            is NavigationState.Deviation -> NavigationState.Navigating(
                route = previousState.lastKnownRoute,
                currentStep = previousState.currentStep,
                distanceToNextManeuver = previousState.distanceToNextManeuver,
                remainingDistance = previousState.remainingDistance,
                remainingDuration = previousState.remainingDuration
            )
            else -> return
        }

        val matchResult = routeMatcher.matchLocationToRoute(
            locationData,
            currentState.route,
            _heading.value
        )

        when (matchResult) {
            is RouteMatcher.MatchResult.OnRoute -> {
                offRouteDetector.observe(matchResult, locationData.timestamp)
                val progress = routeProgressTracker?.update(
                    progressFraction = matchResult.progressFraction,
                    distanceToDestinationMeters = matchResult.distanceToDestinationMeters
                ) ?: return

                _navigationState.value = currentState.copy(
                    currentStep = progress.currentStep,
                    distanceToNextManeuver = progress.distanceToNextManeuverMeters,
                    remainingDistance = progress.remainingDistanceMeters,
                    remainingDuration = progress.remainingDurationSeconds
                )

                // Check if arrived
                if (progress.arrived) {
                    _navigationState.value = NavigationState.Arrived(
                        destination = currentState.route.destinationName
                    )
                    stopLocationTracking()
                }
            }
            is RouteMatcher.MatchResult.OffRoute -> {
                when (matchResult.suggestedAction) {
                    RouteMatcher.SuggestedAction.REROUTE -> {
                        if (offRouteDetector.observe(matchResult, locationData.timestamp)) {
                            _navigationState.value = NavigationState.Deviation(
                                lastKnownRoute = currentState.route,
                                currentStep = currentState.currentStep,
                                distanceToNextManeuver = currentState.distanceToNextManeuver,
                                remainingDistance = currentState.remainingDistance,
                                remainingDuration = currentState.remainingDuration
                            )
                        }
                    }
                    else -> {
                        offRouteDetector.observe(matchResult, locationData.timestamp)
                    }
                }
            }
        }
    }

    fun continueNavigation() {
        val currentState = _navigationState.value
        if (currentState is NavigationState.Deviation) {
            offRouteDetector.reset()
            _navigationState.value = NavigationState.Navigating(
                route = currentState.lastKnownRoute,
                currentStep = currentState.currentStep,
                distanceToNextManeuver = currentState.distanceToNextManeuver,
                remainingDistance = currentState.remainingDistance,
                remainingDuration = currentState.remainingDuration
            )
        }
    }

    /**
     * Starts direct location updates for UI
     */
    private fun startDirectLocationUpdates(): Boolean {
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

        val callback = locationCallback ?: return false
        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            ).addOnFailureListener {
                _locationQuality.value = LocationQuality.STALE
                stopLocationTracking()
            }
            return true
        } catch (e: SecurityException) {
            stopLocationTracking()
            return false
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
        private const val LOCATION_STALE_AFTER_MS = 5000L
        private const val LOCATION_QUALITY_CHECK_INTERVAL_MS = 2000L
        private const val FUTURE_TIMESTAMP_TOLERANCE_MS = 2000L
    }
}
