package com.saferoute.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.saferoute.data.model.NavigationState
import com.saferoute.data.model.NavigationTravelMode
import com.saferoute.navigation.NavigationManager
import com.saferoute.navigation.VoiceGuidancePlayer
import com.saferoute.ui.viewmodel.NavigationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationScreen(
    onNavigateBack: () -> Unit,
    viewModel: NavigationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val fusedLocationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    val navigationState by viewModel.navigationState.collectAsState()
    val acceptedLocation by viewModel.currentLocation.collectAsState()
    val currentSpeed by viewModel.currentSpeed.collectAsState()
    val heading by viewModel.heading.collectAsState()
    val locationQuality by viewModel.locationQuality.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val hasSearchCompleted by viewModel.hasSearchCompleted.collectAsState()
    val searchError by viewModel.searchError.collectAsState()
    val selectedDestination by viewModel.selectedDestination.collectAsState()
    val destinationDetails by viewModel.destinationDetails.collectAsState()
    val isLoadingPlaceDetails by viewModel.isLoadingPlaceDetails.collectAsState()
    val routePreview by viewModel.routePreview.collectAsState()
    val routeOptions by viewModel.routeOptions.collectAsState()
    val selectedRouteIndex by viewModel.selectedRouteIndex.collectAsState()
    val travelMode by viewModel.travelMode.collectAsState()
    val isCalculatingRoute by viewModel.isCalculatingRoute.collectAsState()
    val navigationError by viewModel.navigationError.collectAsState()
    val isRerouting by viewModel.isRerouting.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var voiceGuidanceEnabled by rememberSaveable { mutableStateOf(true) }
    var voiceGuidanceReady by remember { mutableStateOf(false) }
    var isGettingRouteOrigin by remember { mutableStateOf(false) }
    val voiceGuidancePlayer = remember(context) {
        VoiceGuidancePlayer(context) { ready -> voiceGuidanceReady = ready }
    }
    DisposableEffect(voiceGuidancePlayer) {
        onDispose { voiceGuidancePlayer.close() }
    }

    var locationPermissionGranted by rememberSaveable {
        mutableStateOf(hasForegroundLocationPermission(context))
    }
    var preciseLocationPermissionGranted by rememberSaveable {
        mutableStateOf(hasPreciseLocationPermission(context))
    }
    var oneShotLocation by rememberSaveable(stateSaver = latLngSaver) {
        mutableStateOf<LatLng?>(null)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                preciseLocationPermissionGranted = hasPreciseLocationPermission(context)
                locationPermissionGranted = hasForegroundLocationPermission(context)
                viewModel.onPermissionResult(preciseLocationPermissionGranted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Keep a fallback camera position until Android provides a location fix.
    val defaultLocation = LatLng(12.9716, 77.5946)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultLocation, 15f)
    }

    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL,
            isMyLocationEnabled = false,
            isTrafficEnabled = true
        )
    }

    var uiSettings by remember {
        mutableStateOf(
            MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = true,
                mapToolbarEnabled = false,
                zoomGesturesEnabled = true,
                scrollGesturesEnabled = true
            )
        )
    }

    fun moveCameraToLocation(location: Location?) {
        if (location == null) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Waiting for a device location fix.")
            }
            return
        }

        oneShotLocation = LatLng(location.latitude, location.longitude)
        coroutineScope.launch {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    16f
                )
            )
        }
    }

    fun centerMapOnCurrentLocation() {
        if (!hasForegroundLocationPermission(context)) return

        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            if (location != null) {
                moveCameraToLocation(location)
            } else {
                fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
                    moveCameraToLocation(lastLocation)
                }
            }
        }.addOnFailureListener {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Could not get your location. Try again.")
            }
        }
    }

    fun calculateRouteFromFreshLocation() {
        if (!preciseLocationPermissionGranted) {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Precise location is required to calculate a navigation route."
                )
            }
            return
        }

        val now = System.currentTimeMillis()
        val acceptedFix = acceptedLocation?.takeIf {
            locationQuality == NavigationManager.LocationQuality.GOOD &&
                now - it.timestamp in 0..MAX_ROUTE_ORIGIN_AGE_MS &&
                it.accuracy in 0f..MAX_ROUTE_ORIGIN_ACCURACY_METERS
        }
        if (acceptedFix != null) {
            viewModel.calculateRoute(LatLng(acceptedFix.latitude, acceptedFix.longitude))
            return
        }

        isGettingRouteOrigin = true
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            CancellationTokenSource().token
        ).addOnSuccessListener { location ->
            isGettingRouteOrigin = false
            if (location == null || !location.isFreshAccurateNavigationFix()) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Waiting for a fresh, accurate GPS fix. Try again shortly."
                    )
                }
                return@addOnSuccessListener
            }
            val origin = LatLng(location.latitude, location.longitude)
            oneShotLocation = origin
            viewModel.calculateRoute(origin)
        }.addOnFailureListener {
            isGettingRouteOrigin = false
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Could not get a fresh GPS fix. Check location settings and try again."
                )
            }
        }
    }

    val navigationIsActive = navigationState is NavigationState.Navigating ||
        navigationState is NavigationState.Deviation ||
        navigationState is NavigationState.Arrived

    LaunchedEffect(routePreview?.polyline, navigationIsActive) {
        if (navigationIsActive) return@LaunchedEffect
        val points = routePreview?.polyline?.let(::decodeEncodedPolyline).orEmpty()
        if (points.size > 1) {
            val bounds = LatLngBounds.builder().apply {
                points.forEach { point -> include(point) }
            }.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 96))
        }
    }

    LaunchedEffect(navigationState, acceptedLocation, heading, locationQuality) {
        val location = acceptedLocation
        val isNavigationActive = navigationState is NavigationState.Navigating ||
            navigationState is NavigationState.Deviation
        if (
            isNavigationActive && location != null &&
            locationQuality == NavigationManager.LocationQuality.GOOD
        ) {
            val currentCamera = cameraPositionState.position
            val target = LatLng(location.latitude, location.longitude)
            cameraPositionState.animate(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(currentCamera)
                        .target(target)
                        .zoom(maxOf(currentCamera.zoom, NAVIGATION_CAMERA_ZOOM))
                        .bearing(heading ?: currentCamera.bearing)
                        .tilt(NAVIGATION_CAMERA_TILT)
                        .build()
                )
            )
        }
    }

    LaunchedEffect(navigationState, voiceGuidanceEnabled, voiceGuidanceReady) {
        val activeNavigation = navigationState as? NavigationState.Navigating
        if (!voiceGuidanceEnabled || !voiceGuidanceReady || activeNavigation == null) {
            voiceGuidancePlayer.stop()
            return@LaunchedEffect
        }
        viewModel.nextManeuverAnnouncement()?.let(voiceGuidancePlayer::speak)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        preciseLocationPermissionGranted =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        locationPermissionGranted = preciseLocationPermissionGranted ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(preciseLocationPermissionGranted)

        if (locationPermissionGranted) {
            centerMapOnCurrentLocation()
            if (!preciseLocationPermissionGranted) {
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        "Approximate location is enabled. Precise location is recommended for navigation."
                    )
                }
            }
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    "Location permission is needed to show your current location."
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        if (locationPermissionGranted) {
            viewModel.onPermissionResult(preciseLocationPermissionGranted)
            centerMapOnCurrentLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Navigation") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            Column {
                FloatingActionButton(
                    onClick = {
                        if (locationPermissionGranted) {
                            centerMapOnCurrentLocation()
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Map
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = mapProperties,
                uiSettings = uiSettings
            ) {
                val routeToDraw = routePreview ?: when (val state = navigationState) {
                    is NavigationState.Navigating -> state.route
                    is NavigationState.Deviation -> state.lastKnownRoute
                    else -> null
                }
                routeOptions.forEachIndexed { index, route ->
                    if (route != routeToDraw) {
                        val points = remember(route.polyline) {
                            decodeEncodedPolyline(route.polyline)
                        }
                        if (points.isNotEmpty()) {
                            Polyline(
                                points = points,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                width = if (index == selectedRouteIndex) 12f else 8f
                            )
                        }
                    }
                }
                routeToDraw?.let { route ->
                    val routePoints = remember(route.polyline) { decodeEncodedPolyline(route.polyline) }
                    if (routePoints.isNotEmpty()) {
                        Polyline(
                            points = routePoints,
                            color = MaterialTheme.colorScheme.primary,
                            width = 12f
                        )
                    }
                }
                val markerLocation = acceptedLocation
                    ?.takeIf { locationQuality == NavigationManager.LocationQuality.GOOD }
                    ?.let {
                        LatLng(it.latitude, it.longitude)
                    } ?: oneShotLocation
                markerLocation?.let { location ->
                    key(location) {
                        Marker(
                            state = rememberMarkerState(position = location),
                            title = "Current location"
                        )
                    }
                }
            }

            // Search bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                when (val activeState = navigationState) {
                    is NavigationState.Navigating -> ManeuverGuidanceCard(
                        instruction = activeState.route.steps.getOrNull(
                            if (
                                activeState.distanceToNextManeuver > 0 &&
                                activeState.currentStep < activeState.route.steps.lastIndex
                            ) {
                                activeState.currentStep + 1
                            } else {
                                activeState.currentStep
                            }
                        )?.instruction ?: "Continue on your route",
                        distanceToManeuver = activeState.distanceToNextManeuver,
                        warnings = activeState.route.warnings,
                        voiceEnabled = voiceGuidanceEnabled,
                        voiceAvailable = voiceGuidanceReady,
                        onToggleVoice = { voiceGuidanceEnabled = !voiceGuidanceEnabled }
                    )
                    is NavigationState.Deviation -> Text(
                        text = "Off route. Follow the highlighted route to resume guidance.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    else -> {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { query ->
                                viewModel.onSearchQueryChange(
                                    query = query,
                                    latitude = oneShotLocation?.latitude,
                                    longitude = oneShotLocation?.longitude
                                )
                            },
                            placeholder = "Search destination..."
                        )

                        if (searchResults.isNotEmpty() || isSearching || searchError != null ||
                            hasSearchCompleted
                        ) {
                            SearchResultsList(
                                results = searchResults,
                                isLoading = isSearching,
                                error = searchError,
                                showEmpty = hasSearchCompleted && searchResults.isEmpty() &&
                                    searchError == null,
                                onResultSelected = viewModel::selectDestination
                            )
                        }
                        if (selectedDestination != null &&
                            navigationState !is NavigationState.Arrived
                        ) {
                            RouteSelectionCard(
                                placeName = destinationDetails?.name
                                    ?: selectedDestination?.name.orEmpty(),
                                address = destinationDetails?.address.orEmpty(),
                                canCalculateRoute = destinationDetails != null,
                                isLoadingPlace = isLoadingPlaceDetails,
                                route = routePreview,
                                routes = routeOptions,
                                selectedRouteIndex = selectedRouteIndex,
                                travelMode = travelMode,
                                isCalculatingRoute = isCalculatingRoute || isGettingRouteOrigin,
                                error = navigationError,
                                onCalculateRoute = ::calculateRouteFromFreshLocation,
                                onTravelModeSelected = viewModel::setTravelMode,
                                onRouteSelected = viewModel::selectRoute,
                                onStartNavigation = {
                                    val route = routePreview
                                    if (route != null && viewModel.startNavigation(route)) {
                                        oneShotLocation = null
                                    }
                                },
                                onClear = viewModel::clearDestination
                            )
                        }
                    }
                }
            }

            // Speed indicator
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                SpeedIndicator(speedKmh = currentSpeed)
                if (navigationState is NavigationState.Navigating) {
                    Text(
                        text = when (locationQuality) {
                            NavigationManager.LocationQuality.SEARCHING -> "Waiting for GPS"
                            NavigationManager.LocationQuality.GOOD -> "GPS signal good"
                            NavigationManager.LocationQuality.POOR -> "GPS accuracy is low"
                            NavigationManager.LocationQuality.STALE ->
                                "GPS signal lost; waiting for update"
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            // Navigation state UI
            when (val state = navigationState) {
                is NavigationState.Navigating -> {
                    NavigationInfoCard(
                        remainingDistance = state.remainingDistance,
                        remainingDuration = state.remainingDuration,
                        onStop = viewModel::stopNavigation,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
                is NavigationState.Deviation -> {
                    OffRoutePrompt(
                        onReroute = viewModel::requestReroute,
                        onContinue = viewModel::continueNavigation,
                        onStop = viewModel::stopNavigation,
                        isRerouting = isRerouting,
                        error = navigationError,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
                is NavigationState.Arrived -> {
                    ArrivalInfoCard(
                        destination = state.destination,
                        onDone = viewModel::stopNavigation,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    )
                }
                else -> { /* No special UI for other states */ }
            }
        }
    }
}

private fun hasForegroundLocationPermission(context: Context): Boolean {
    return hasPreciseLocationPermission(context) || ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun hasPreciseLocationPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

private fun Location.isFreshAccurateNavigationFix(
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val ageMillis = nowMillis - time
    return latitude.isFinite() && longitude.isFinite() &&
        latitude in -90.0..90.0 && longitude in -180.0..180.0 &&
        hasAccuracy() && accuracy.isFinite() &&
        accuracy in 0f..MAX_ROUTE_ORIGIN_ACCURACY_METERS &&
        ageMillis in 0..MAX_ROUTE_ORIGIN_AGE_MS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search"
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp)
        )
    }
}

@Composable
private fun SearchResultsList(
    results: List<NavigationViewModel.SearchResult>,
    isLoading: Boolean,
    error: String?,
    showEmpty: Boolean,
    onResultSelected: (NavigationViewModel.SearchResult) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column {
                error?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error
                    )
                }
                if (showEmpty) {
                    Text("No places found. Try another search.", modifier = Modifier.padding(16.dp))
                }
                results.forEach { result ->
                    SearchResultItem(
                        result = result,
                        onClick = { onResultSelected(result) }
                    )
                }
                Text(
                    text = "Powered by Google",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    result: NavigationViewModel.SearchResult,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Column(
            modifier = Modifier.padding(start = 16.dp)
        ) {
            Text(
                text = result.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = result.address,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun SpeedIndicator(
    speedKmh: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = CircleShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${speedKmh.toInt()}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun NavigationInfoCard(
    remainingDistance: Int,
    remainingDuration: Int,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Navigation,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column {
                Text(
                    text = formatDistance(remainingDistance),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatDuration(remainingDuration),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = onStop) {
                    Text("End navigation")
                }
            }
        }
    }
}

@Composable
private fun ManeuverGuidanceCard(
    instruction: String,
    distanceToManeuver: Int,
    warnings: List<String>,
    voiceEnabled: Boolean,
    voiceAvailable: Boolean,
    onToggleVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.size(12.dp))
                Column {
                    Text(
                        text = instruction,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (distanceToManeuver <= 0) {
                            "Now"
                        } else {
                            "In ${formatDistance(distanceToManeuver)}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            warnings.forEach { warning ->
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            TextButton(onClick = onToggleVoice, enabled = voiceAvailable) {
                Text(
                    when {
                        !voiceAvailable -> "Voice guidance unavailable"
                        voiceEnabled -> "Mute voice guidance"
                        else -> "Enable voice guidance"
                    }
                )
            }
        }
    }
}

@Composable
private fun ArrivalInfoCard(
    destination: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "You have arrived",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = destination,
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onDone) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun OffRoutePrompt(
    onReroute: () -> Unit,
    onContinue: () -> Unit,
    onStop: () -> Unit,
    isRerouting: Boolean,
    error: String?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Off route",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Text(
                text = "GPS confirms you are away from the planned route.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row {
                Button(onClick = onReroute, enabled = !isRerouting) {
                    if (isRerouting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("Recalculate route")
                    }
                }
                TextButton(onClick = onContinue, enabled = !isRerouting) {
                    Text("Continue on planned route")
                }
            }
            TextButton(onClick = onStop) {
                Text("End navigation")
            }
        }
    }
}

private fun formatDistance(meters: Int): String {
    return when {
        meters < 1000 -> "$meters m"
        else -> "${meters / 1000}.${(meters % 1000) / 100} km"
    }
}

private const val NAVIGATION_CAMERA_ZOOM = 17f
private const val NAVIGATION_CAMERA_TILT = 45f
private const val MAX_ROUTE_ORIGIN_AGE_MS = 5_000L
private const val MAX_ROUTE_ORIGIN_ACCURACY_METERS = 50f

private val latLngSaver = Saver<LatLng?, Bundle>(
    save = { location ->
        location?.let {
            Bundle().apply {
                putDouble("latitude", it.latitude)
                putDouble("longitude", it.longitude)
            }
        }
    },
    restore = { bundle ->
        LatLng(bundle.getDouble("latitude"), bundle.getDouble("longitude"))
    }
)

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        else -> "$minutes min"
    }
}

@Composable
private fun RouteSelectionCard(
    placeName: String,
    address: String,
    canCalculateRoute: Boolean,
    isLoadingPlace: Boolean,
    route: com.saferoute.data.model.Route?,
    routes: List<com.saferoute.data.model.Route>,
    selectedRouteIndex: Int,
    travelMode: NavigationTravelMode,
    isCalculatingRoute: Boolean,
    error: String?,
    onCalculateRoute: () -> Unit,
    onTravelModeSelected: (NavigationTravelMode) -> Unit,
    onRouteSelected: (Int) -> Unit,
    onStartNavigation: () -> Unit,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(placeName, style = MaterialTheme.typography.titleMedium)
            if (address.isNotBlank()) {
                Text(address, style = MaterialTheme.typography.bodySmall)
            }
            Row(modifier = Modifier.padding(top = 8.dp)) {
                NavigationTravelMode.values().forEach { mode ->
                    if (mode == travelMode) {
                        Button(onClick = { onTravelModeSelected(mode) }) {
                            Text(if (mode == NavigationTravelMode.DRIVE) "Car" else "Motorcycle")
                        }
                    } else {
                        OutlinedButton(onClick = { onTravelModeSelected(mode) }) {
                            Text(if (mode == NavigationTravelMode.DRIVE) "Car" else "Motorcycle")
                        }
                    }
                }
            }
            if (routes.size > 1) {
                Text(
                    text = "Choose a route",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
                routes.forEachIndexed { index, option ->
                    val label = if (index == 0) "Recommended" else "Alternative ${index + 1}"
                    val buttonLabel = "$label · ${formatDistance(option.distanceMeters)} · " +
                        formatDuration(option.durationSeconds)
                    if (index == selectedRouteIndex) {
                        Button(
                            onClick = { onRouteSelected(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(buttonLabel) }
                    } else {
                        OutlinedButton(
                            onClick = { onRouteSelected(index) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(buttonLabel) }
                    }
                }
            }
            when {
                isLoadingPlace || isCalculatingRoute -> CircularProgressIndicator()
                route != null -> {
                    Text("${formatDistance(route.distanceMeters)} · ${formatDuration(route.durationSeconds)}")
                    route.warnings.forEach { warning ->
                        Text(warning, style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = onStartNavigation) { Text("Start navigation") }
                }
                else -> Button(
                    onClick = onCalculateRoute,
                    enabled = canCalculateRoute
                ) {
                    Text("Get route")
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedButton(onClick = onClear) { Text("Clear destination") }
        }
    }
}

private fun decodeEncodedPolyline(encoded: String): List<LatLng> {
    val points = mutableListOf<LatLng>()
    var index = 0
    var latitude = 0
    var longitude = 0

    while (index < encoded.length) {
        var result = 0
        var shift = 0
        var byte: Int
        do {
            if (index >= encoded.length) return emptyList()
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        latitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1

        result = 0
        shift = 0
        do {
            if (index >= encoded.length) return emptyList()
            byte = encoded[index++].code - 63
            result = result or ((byte and 0x1f) shl shift)
            shift += 5
        } while (byte >= 0x20)
        longitude += if ((result and 1) != 0) (result shr 1).inv() else result shr 1
        points += LatLng(latitude / 1e5, longitude / 1e5)
    }
    return points
}
