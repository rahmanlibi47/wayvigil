package com.saferoute.ui.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.FusedLocationProviderClient
import com.saferoute.data.model.CreateHazardReportRequest
import com.saferoute.data.model.HazardType
import com.saferoute.data.model.Location
import com.saferoute.data.remote.SafeRouteApi
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class ReportHazardViewModel @Inject constructor(
    private val safeRouteApi: SafeRouteApi,
    private val fusedLocationClient: FusedLocationProviderClient,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    @SuppressLint("MissingPermission")
    fun submitReport(
        hazardType: HazardType,
        description: String?,
        onSuccess: () -> Unit
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null

            try {
                // Get current location
                val locationResult = fusedLocationClient.lastLocation.await()

                if (locationResult == null) {
                    _error.value = "Unable to get location. Please try again."
                    return@launch
                }

                val location = Location(
                    latitude = locationResult.latitude,
                    longitude = locationResult.longitude,
                    accuracy = locationResult.accuracy
                )

                // Create report request
                val request = CreateHazardReportRequest(
                    hazardType = hazardType.name,
                    description = description,
                    location = location,
                    mediaUrls = null,
                    deviceId = null,
                    appVersion = context.packageManager.getPackageInfo(
                        context.packageName,
                        0
                    ).versionName
                )

                // Submit to API
                safeRouteApi.createHazardReport(request)

                onSuccess()
            } catch (e: Exception) {
                val errorMessage = e.message ?: "Failed to submit report"
                _error.value = errorMessage
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}
