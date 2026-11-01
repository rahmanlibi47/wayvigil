package com.saferoute.data.remote

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "device_prefs")

/**
 * Manages persistent device identification across app sessions.
 * Device ID is generated once and stored in DataStore for consistency.
 */
@Singleton
class DeviceIdManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore

    companion object {
        private val DEVICE_ID_KEY = stringPreferencesKey("device_id")
        private const val TAG = "DeviceIdManager"
    }

    /**
     * Gets the device ID, generating and persisting a new one if it doesn't exist.
     * This ensures the same device ID is used across all app sessions.
     */
    suspend fun getDeviceId(): String {
        // Try to get existing device ID
        val existingId = dataStore.data.map { prefs ->
            prefs[DEVICE_ID_KEY]
        }.first()

        if (existingId != null) {
            return existingId
        }

        // Generate new device ID and persist it
        val newDeviceId = generateDeviceId()
        dataStore.edit { prefs ->
            prefs[DEVICE_ID_KEY] = newDeviceId
        }
        android.util.Log.d(TAG, "Generated new device ID: $newDeviceId")
        return newDeviceId
    }

    /**
     * Generates a unique device identifier.
     * Uses UUID v4 for uniqueness and privacy (no device hardware info exposed).
     */
    private fun generateDeviceId(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * Resets the device ID, generating a new one on next access.
     * Use with caution - this breaks continuity for analytics and user tracking.
     */
    suspend fun resetDeviceId() {
        dataStore.edit { prefs ->
            prefs.remove(DEVICE_ID_KEY)
        }
        android.util.Log.d(TAG, "Device ID reset")
    }
}
