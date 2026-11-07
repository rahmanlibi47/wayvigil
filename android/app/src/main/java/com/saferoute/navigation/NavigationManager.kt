package com.saferoute.navigation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
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
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

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

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val headingEstimator = HeadingEstimator()
    private val accelerometerValues = FloatArray(3)
    private val magneticFieldValues = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val displayRotationMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)
    private var hasAccelerometerValues = false
    private var hasMagneticFieldValues = false
    private var accelerometerUpdatedAt = 0L
    private var magneticFieldUpdatedAt = 0L
    private var latestCourseDegrees: Float? = null
    private var latestCourseAccuracyDegrees: Float? = null
    private var magneticDeclinationDegrees = 0f
    private val unreliableSensorTypes = mutableSetOf<Int>()
    private var orientationSensorsRegistered = false

    // Navigation logic components
    private val speedCalculator = SpeedCalculator()
    private val routeMatcher = RouteMatcher()
    private val offRouteDetector = OffRouteDetector()
    private var routeProgressTracker: RouteProgressTracker? = null

    // Location tracking
    private var locationCallback: LocationCallback? = null
    private var isTracking = false
    private var trackingStartedAt = 0L
    private val orientationSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (!isTracking) return
            if (
                event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
                event.sensor.type in unreliableSensorTypes
            ) {
                resetFallbackSensorValue(event.sensor.type)
                clearUnreliableDeviceHeading()
                return
            }
            when (event.sensor.type) {
                Sensor.TYPE_ROTATION_VECTOR -> {
                    SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                }
                Sensor.TYPE_ACCELEROMETER -> {
                    event.values.copyInto(accelerometerValues)
                    hasAccelerometerValues = true
                    accelerometerUpdatedAt = System.currentTimeMillis()
                    updateFallbackOrientation(rotationMatrix)
                    return
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    event.values.copyInto(magneticFieldValues)
                    hasMagneticFieldValues = true
                    magneticFieldUpdatedAt = System.currentTimeMillis()
                    updateFallbackOrientation(rotationMatrix)
                    return
                }
                else -> return
            }
            publishDeviceHeading(rotationMatrix, System.currentTimeMillis())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            if (!isTracking) return
            val sensorType = sensor?.type ?: return
            if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                unreliableSensorTypes += sensorType
                resetFallbackSensorValue(sensorType)
                clearUnreliableDeviceHeading()
            } else {
                unreliableSensorTypes -= sensorType
            }
        }
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val staleLocationCheck = object : Runnable {
        override fun run() {
            if (!isTracking) return
            val lastFix = _currentLocation.value
            val lastUpdateTime = lastFix?.timestamp ?: trackingStartedAt
            if (System.currentTimeMillis() - lastUpdateTime > LOCATION_STALE_AFTER_MS) {
                _locationQuality.value = LocationQuality.STALE
                _currentSpeed.value = speedCalculator.getFilteredSpeed()
                headingEstimator.reset()
                _heading.value = null
                latestCourseDegrees = null
                latestCourseAccuracyDegrees = null
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
            _currentLocation.value = null
            _currentSpeed.value = 0f
            headingEstimator.reset()
            _heading.value = null
            latestCourseDegrees = null
            latestCourseAccuracyDegrees = null
            unreliableSensorTypes.clear()
            startOrientationSensors()
            _locationQuality.value = LocationQuality.SEARCHING
            mainHandler.removeCallbacks(staleLocationCheck)
            mainHandler.postDelayed(staleLocationCheck, LOCATION_QUALITY_CHECK_INTERVAL_MS)
            if (startDirectLocationUpdates()) {
                true
            } else {
                stopLocationTracking()
                false
            }
        } catch (e: SecurityException) {
            stopLocationTracking()
            false
        }
    }

    /**
     * Stops location tracking
     */
    private fun stopLocationTracking() {
        mainHandler.removeCallbacks(staleLocationCheck)
        val wasTracking = isTracking
        isTracking = false
        stopDirectLocationUpdates()
        stopOrientationSensors()
        headingEstimator.reset()
        _heading.value = null
        latestCourseDegrees = null
        latestCourseAccuracyDegrees = null
        unreliableSensorTypes.clear()
        if (!wasTracking) return

        val serviceIntent = Intent(context, LocationTrackingService::class.java)
        context.stopService(serviceIntent)

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
        magneticDeclinationDegrees = GeomagneticField(
            locationData.latitude.toFloat(),
            locationData.longitude.toFloat(),
            locationData.altitude.toFloat(),
            System.currentTimeMillis()
        ).declination
        val course = location.bearing.takeIf { location.hasBearing() }
        val courseAccuracy = if (location.hasBearingAccuracy()) {
            location.bearingAccuracyDegrees
        } else {
            null
        }
        latestCourseDegrees = course
        latestCourseAccuracyDegrees = courseAccuracy
        _heading.value = headingEstimator.update(
            courseDegrees = course,
            courseAccuracyDegrees = courseAccuracy,
            speedMetersPerSecond = location.speed,
            timestamp = location.time
        )

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
                if (isTracking) result.lastLocation?.let(::updateLocation)
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

    private fun startOrientationSensors() {
        if (!isTracking || orientationSensorsRegistered) return

        orientationSensorsRegistered = rotationVectorSensor?.let {
            sensorManager.registerListener(
                orientationSensorListener,
                it,
                ORIENTATION_SENSOR_DELAY_MICROS
            )
        } ?: false
        if (orientationSensorsRegistered) return

        val accelerometerRegistered = accelerometerSensor?.let {
            sensorManager.registerListener(
                orientationSensorListener,
                it,
                ORIENTATION_SENSOR_DELAY_MICROS
            )
        } ?: false
        val magnetometerRegistered = magnetometerSensor?.let {
            sensorManager.registerListener(
                orientationSensorListener,
                it,
                ORIENTATION_SENSOR_DELAY_MICROS
            )
        } ?: false
        orientationSensorsRegistered = accelerometerRegistered && magnetometerRegistered
        if (!orientationSensorsRegistered) {
            sensorManager.unregisterListener(orientationSensorListener)
        }
    }

    private fun stopOrientationSensors() {
        sensorManager.unregisterListener(orientationSensorListener)
        orientationSensorsRegistered = false
        hasAccelerometerValues = false
        hasMagneticFieldValues = false
        accelerometerUpdatedAt = 0L
        magneticFieldUpdatedAt = 0L
        accelerometerValues.fill(0f)
        magneticFieldValues.fill(0f)
    }

    private fun updateFallbackOrientation(rotationMatrix: FloatArray) {
        if (!hasAccelerometerValues || !hasMagneticFieldValues) return
        val now = System.currentTimeMillis()
        if (
            now - accelerometerUpdatedAt !in 0..ORIENTATION_SENSOR_MAX_AGE_MS ||
            now - magneticFieldUpdatedAt !in 0..ORIENTATION_SENSOR_MAX_AGE_MS
        ) {
            return
        }
        if (SensorManager.getRotationMatrix(
                rotationMatrix,
                null,
                accelerometerValues,
                magneticFieldValues
            )
        ) {
            publishDeviceHeading(rotationMatrix, System.currentTimeMillis())
        }
    }

    private fun resetFallbackSensorValue(sensorType: Int) {
        when (sensorType) {
            Sensor.TYPE_ACCELEROMETER -> {
                hasAccelerometerValues = false
                accelerometerUpdatedAt = 0L
                accelerometerValues.fill(0f)
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                hasMagneticFieldValues = false
                magneticFieldUpdatedAt = 0L
                magneticFieldValues.fill(0f)
            }
        }
    }

    private fun publishDeviceHeading(rotationMatrix: FloatArray, timestamp: Long) {
        val axes = when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            Surface.ROTATION_0 -> SensorManager.AXIS_X to SensorManager.AXIS_Y
            else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        if (!SensorManager.remapCoordinateSystem(
                rotationMatrix,
                axes.first,
                axes.second,
                displayRotationMatrix
            )
        ) {
            return
        }
        SensorManager.getOrientation(displayRotationMatrix, orientationValues)
        val magneticHeading = Math.toDegrees(orientationValues[0].toDouble()).toFloat()
        val location = _currentLocation.value ?: return
        if (
            _locationQuality.value != LocationQuality.GOOD ||
            System.currentTimeMillis() - location.timestamp !in 0..LOCATION_STALE_AFTER_MS
        ) {
            return
        }
        headingEstimator.updateDeviceHeading(
            magneticHeading + magneticDeclinationDegrees,
            timestamp
        )
        val speed = _currentLocation.value?.speed ?: 0f
        _heading.value = headingEstimator.update(
            courseDegrees = latestCourseDegrees,
            courseAccuracyDegrees = latestCourseAccuracyDegrees,
            speedMetersPerSecond = speed,
            timestamp = timestamp
        )
    }

    private fun clearUnreliableDeviceHeading() {
        headingEstimator.clearDeviceHeading()
        val location = _currentLocation.value
        _heading.value = if (location == null || _locationQuality.value != LocationQuality.GOOD) {
            null
        } else {
            headingEstimator.update(
                courseDegrees = latestCourseDegrees,
                courseAccuracyDegrees = latestCourseAccuracyDegrees,
                speedMetersPerSecond = location.speed,
                timestamp = System.currentTimeMillis()
            )
        }
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
        private const val ORIENTATION_SENSOR_DELAY_MICROS = 50_000
        private const val ORIENTATION_SENSOR_MAX_AGE_MS = 2000L
    }
}
