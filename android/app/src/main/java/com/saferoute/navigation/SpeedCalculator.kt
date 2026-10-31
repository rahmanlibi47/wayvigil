package com.saferoute.navigation

import com.saferoute.data.model.LocationData
import com.saferoute.data.model.SpeedReading
import kotlin.math.abs

/**
 * Calculates speed from GPS readings with filtering.
 * Uses a simple weighted moving average with quality-based weighting.
 */
class SpeedCalculator(
    private val windowSize: Int = DEFAULT_WINDOW_SIZE,
    private val maxSpeedChange: Float = DEFAULT_MAX_SPEED_CHANGE_KMH
) {
    private val readings = ArrayDeque<SpeedReading>(windowSize)

    /**
     * Adds a new speed reading and returns filtered speed
     */
    fun addReading(speedKmh: Float, accuracy: Float): Float {
        val reading = SpeedReading(speedKmh, accuracy, System.currentTimeMillis())

        // Remove old readings
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
     * Adds a location reading and extracts speed
     */
    fun addLocation(location: LocationData): Float {
        return if (location.hasValidSpeed()) {
            addReading(location.speedKmh, location.speedAccuracy)
        } else {
            // No valid speed from GPS, use calculated speed if available
            getFilteredSpeed()
        }
    }

    /**
     * Gets the current filtered speed using weighted average
     */
    fun getFilteredSpeed(): Float {
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
     * Clears all readings
     */
    fun clear() {
        readings.clear()
    }

    companion object {
        const val DEFAULT_WINDOW_SIZE = 5
        const val DEFAULT_MAX_SPEED_CHANGE_KMH = 30f // Maximum believable speed change between readings
    }
}
