package com.saferoute.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferoute.data.remote.SafeRouteApi
import com.saferoute.data.remote.TokenManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val safeRouteApi: SafeRouteApi,
    private val tokenManager: TokenManager
) : ViewModel() {

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        checkAuthStatus()
    }

    private fun checkAuthStatus() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val token = tokenManager.getToken()
                _isAuthenticated.value = token != null
            } catch (e: Exception) {
                _isAuthenticated.value = false
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun anonymousLogin(deviceId: String, appVersion: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
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

    fun signOut() {
        viewModelScope.launch {
            tokenManager.clearToken()
            _isAuthenticated.value = false
        }
    }

    fun clearError() {
        _error.value = null
    }
}
