package com.saferoute.navigation

import com.saferoute.data.model.LocationData
import com.saferoute.data.model.SpeedReading
import kotlin.math.abs

/**
 * Calculates speed from GPS readings with filtering.
 * Uses a simple weighted moving average with quality-based weighting.
 * Automatically clears stale readings to prevent outdated data from affecting calculations.
 */
class SpeedCalculator(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val maxSpeedChange: Float = DEFAULT_MAX_SPEED_CHANGE_KMH,
    private val maxReadingAgeMs: Long = DEFAULT_MAX_READING_AGE_MS
) {
    private val readings = ArrayDeque<SpeedReading>(windowSize)

    /**
     * Adds a new speed reading and returns filtered speed.
     * Stale readings (older than maxReadingAgeMs) are automatically removed.
     */
    fun addReading(speedKmh: Float, accuracy: Float): Float {
        val currentTime = System.currentTimeMillis()

        // Remove stale readings first
        removeStaleReadings(currentTime)

        val reading = SpeedReading(speedKmh, accuracy, currentTime)

        // Remove old readings if window is full
        while (readings.size >= windowSize) {
            readings.removeFirst()
        }

        // Validate against recent readings for sudden jumps
        val lastValidSpeed = getFilteredSpeed()
        if (lastValidSpeed > 0 && readings.isNotEmpty()) {
            val speedChange = abs(speedKmh - lastValidSpeed)
            if (speedChange > maxSpeedChange) {
                // Reject reading with large sudden change unless accuracy is very good
                if (accuracy > 2f) {
                    return lastValidSpeed
                }
            }
        }

        readings.addLast(reading)
        return getFilteredSpeed()
    }

    /**
     * Adds a location reading and extracts speed.
     * Also clears stale readings to ensure accurate calculations after GPS gaps.
     */
    fun addLocation(location: LocationData): Float {
        // Remove stale readings based on current time
        removeStaleReadings(System.currentTimeMillis())

        return if (location.hasValidSpeed()) {
            addReading(location.speedKmh, location.speedAccuracy)
        } else {
            // No valid speed from GPS, use calculated speed if available
            getFilteredSpeed()
        }
    }

    /**
     * Gets the current filtered speed using weighted average.
     * Only considers non-stale readings.
     */
    fun getFilteredSpeed(): Float {
        // Remove stale readings before calculation
        removeStaleReadings(System.currentTimeMillis())

        if (readings.isEmpty()) return 0f

        var totalWeight = 0f
        var weightedSum = 0f

        readings.forEach { reading ->
            val weight = reading.quality
            weightedSum += reading.speedKmh * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) weightedSum / totalWeight else 0f
    }

    /**
     * Removes readings that are older than the maximum age threshold.
     * This prevents stale data from affecting speed calculations after GPS gaps.
     */
    private fun removeStaleReadings(currentTime: Long) {
        val cutoffTime = currentTime - maxReadingAgeMs
        while (readings.isNotEmpty() && readings.first().timestamp < cutoffTime) {
            readings.removeFirst()
        }
    }

    /**
     * Returns the number of active (non-stale) readings in the buffer.
     */
    fun getActiveReadingCount(): Int {
        removeStaleReadings(System.currentTimeMillis())
        return readings.size
    }

    /**
     * Returns the age of the oldest reading in milliseconds.
     * Returns 0 if no readings exist.
     */
    fun getOldestReadingAgeMs(): Long {
        if (readings.isEmpty()) return 0
        val oldest = readings.minByOrNull { it.timestamp }
        return oldest?.let { System.currentTimeMillis() - it.timestamp } ?: 0
    }

    /**
     * Clears all readings
     */
    fun clear() {
        readings.clear()
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 5
        const val DEFAULT_MAX_SPEED_CHANGE_KMH = 30f // Maximum believable speed change between readings
        const val DEFAULT_MAX_READING_AGE_MS = 5000L // 5 seconds - readings older than this are discarded
    }
}
