package com.saferoute.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferoute.data.remote.DeviceIdManager
import com.saferoute.data.remote.SafeRouteApi
import com.saferoute.data.remote.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val safeRouteApi: SafeRouteApi,
    private val tokenManager: TokenManager,
    private val deviceIdManager: DeviceIdManager
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        viewModelScope.launch {
            try {
                var hadSession = false
                tokenManager.tokenFlow.collect { token ->
                    val authenticated = token != null
                    if (hadSession && !authenticated) {
                        _error.value = "Your session expired. Please sign in again."
                    }
                    hadSession = authenticated
                    _isAuthenticated.value = authenticated
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isAuthenticated.value = false
                _isLoading.value = false
            }
        }
    }

    /**
     * Performs anonymous login with a persistent device ID.
     * The device ID is retrieved from storage or generated if it doesn't exist.
     */
    fun anonymousLogin(appVersion: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Get persistent device ID (generates and stores if not exists)
                val deviceId = deviceIdManager.getDeviceId()

                val response = safeRouteApi.anonymousLogin(deviceId, appVersion)
                tokenManager.saveToken(response.accessToken)
                tokenManager.saveUserId(response.user.id)
                _isAuthenticated.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Login failed"
                _isAuthenticated.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Signs out the current user and clears the session.
     * Note: Device ID is preserved for analytics consistency.
     */
    fun signOut() {
        viewModelScope.launch {
            tokenManager.clearToken()
            _isAuthenticated.value = false
        }
    }

    /**
     * Clears any error message.
     */
    fun clearError() {
        _error.value = null
    }
}
