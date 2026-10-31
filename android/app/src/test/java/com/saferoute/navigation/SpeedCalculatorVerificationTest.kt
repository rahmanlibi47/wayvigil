package com.saferoute.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification tests for SpeedCalculator.
 * Tests various GPS update intervals, stationary readings, acceleration patterns,
 * and edge cases.
 */
class SpeedCalculatorVerificationTest {

    private lateinit var speedCalculator: SpeedCalculator

    @Before
    fun setup() {
        speedCalculator = SpeedCalculator()
    }

    // ==================== GPS Update Intervals ====================

    @Test
    fun `handles 1Hz GPS updates correctly`() {
        // Simulate 1Hz updates (1 second intervals)
        speedCalculator.addReading(50f, 1f)
        speedCalculator.addReading(51f, 1f)
        speedCalculator.addReading(50.5f, 1f)
        speedCalculator.addReading(52f, 1f)
        speedCalculator.addReading(51f, 1f)

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 50f..52f)
    }

    @Test
    fun `handles 5Hz GPS updates correctly`() {
        // Simulate 5Hz updates (200ms intervals)
        repeat(20) { i ->
            val speed = 50f + (i % 3) // Small variations
            speedCalculator.addReading(speed, 0.5f)
        }

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 49f..53f)
    }

    @Test
    fun `handles slow 0_5Hz GPS updates correctly`() {
        // Simulate 0.5Hz updates (2 second intervals)
        speedCalculator.addReading(50f, 1f)
        speedCalculator.addReading(52f, 1f)

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 50f..52f)
    }

    // ==================== Stationary Readings ====================

    @Test
    fun `handles stationary readings with zero speed`() {
        repeat(5) {
            val result = speedCalculator.addReading(0f, 1f)
            if (it == 0) {
                assertEquals(0f, result, 0.1f)
            }
        }

        val result = speedCalculator.getFilteredSpeed()
        assertEquals(0f, result, 0.1f)
    }

    @Test
    fun `handles stationary readings with low noise`() {
        // GPS often shows 1-3 km/h when stationary
        speedCalculator.addReading(0f, 1f)
        speedCalculator.addReading(2f, 2f)
        speedCalculator.addReading(1f, 1.5f)
        speedCalculator.addReading(0f, 1f)
        speedCalculator.addReading(3f, 2f)

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 0f..3f)
    }

    // ==================== Gradual Acceleration ====================

    @Test
    fun `handles gradual acceleration from stop`() {
        val speeds = listOf(0f, 5f, 12f, 20f, 30f, 40f, 50f)

        speeds.forEach { speed ->
            speedCalculator.addReading(speed, 1f)
        }

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 25f..50f) // Should show weighted average
    }

    @Test
    fun `handles gradual acceleration at highway speeds`() {
        val speeds = listOf(60f, 65f, 70f, 75f, 80f, 85f)

        speeds.forEach { speed ->
            speedCalculator.addReading(speed, 1f)
        }

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 65f..85f)
    }

    @Test
    fun `handles gradual deceleration`() {
        val speeds = listOf(80f, 75f, 70f, 60f, 50f, 40f, 30f, 20f)

        speeds.forEach { speed ->
            speedCalculator.addReading(speed, 1f)
        }

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 40f..65f)
    }

    // ==================== Sudden Speed Spikes ====================

    @Test
    fun `filters unrealistic speed spike to_100_from_50`() {
        // Establish baseline
        repeat(3) { speedCalculator.addReading(50f, 1f) }

        // Sudden spike (would require impossible acceleration)
        val result = speedCalculator.addReading(100f, 5f)

        // Should reject or heavily dampen
        assertTrue(result < 75f)
    }

    @Test
    fun `filters unrealistic speed drop from_80_to_10`() {
        // Establish baseline at highway speed
        repeat(3) { speedCalculator.addReading(80f, 1f) }

        // Sudden drop (emergency braking still wouldn't be this fast)
        val result = speedCalculator.addReading(10f, 5f)

        // Should reject sudden drop
        assertTrue(result > 40f)
    }

    @Test
    fun `accepts realistic emergency braking`() {
        // Establish baseline at 80 km/h
        repeat(3) { speedCalculator.addReading(80f, 1f) }

        // Hard braking is possible over multiple readings
        speedCalculator.addReading(60f, 2f)
        speedCalculator.addReading(40f, 2f)
        speedCalculator.addReading(25f, 2f)

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 20f..60f)
    }

    // ==================== Poor GPS Accuracy ====================

    @Test
    fun `reduces weight of poor_accuracy_readings`() {
        // High quality baseline
        speedCalculator.addReading(50f, 0.5f)
        speedCalculator.addReading(51f, 0.5f)
        speedCalculator.addReading(50f, 0.5f)

        // Poor accuracy outlier
        speedCalculator.addReading(70f, 10f)

        val result = speedCalculator.getFilteredSpeed()
        // Should stay close to baseline despite outlier
        assertTrue(result in 48f..55f)
    }

    @Test
    fun `handles mixed_accuracy_readings`() {
        // Mix of good and poor accuracy
        speedCalculator.addReading(50f, 0.5f) // Excellent
        speedCalculator.addReading(52f, 1.0f) // Good
        speedCalculator.addReading(48f, 3.0f) // Fair
        speedCalculator.addReading(55f, 8.0f) // Poor
        speedCalculator.addReading(51f, 1.0f) // Good

        val result = speedCalculator.getFilteredSpeed()
        // Should be weighted toward better accuracy readings
        assertTrue(result in 49f..53f)
    }

    // ==================== High Speed Readings ====================

    @Test
    fun `handles realistic highway_speed_120kmh`() {
        val result = speedCalculator.addReading(120f, 1f)
        assertEquals(120f, result, 0.1f)
    }

    @Test
    fun `handles_high_speed_with_low_accuracy`() {
        // Highway speed with varying accuracy
        speedCalculator.addReading(110f, 2f)
        speedCalculator.addReading(115f, 1f)
        speedCalculator.addReading(112f, 3f)
        speedCalculator.addReading(118f, 1f)

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 110f..120f)
    }

    @Test
    fun `filters_high_speed_spikes`() {
        // Cruising at 100 km/h
        repeat(3) { speedCalculator.addReading(100f, 1f) }

        // Impossible spike to 200 km/h
        val result = speedCalculator.addReading(200f, 5f)

        assertTrue(result < 150f)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles_negative_speed_from_GPS_errors`() {
        speedCalculator.addReading(50f, 1f)

        val result = speedCalculator.addReading(-5f, 2f)

        // Negative speed should either be rejected or handled gracefully
        assertTrue(result >= 0f)
    }

    @Test
    fun `handles_very_high_accuracy_claims`() {
        // GPS claiming very high accuracy
        speedCalculator.addReading(50f, 0.1f)
        speedCalculator.addReading(51f, 0.1f)

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 49f..52f)
    }

    @Test
    fun `handles_window_overflow_correctly`() {
        // Add more readings than window size
        repeat(10) { i ->
            speedCalculator.addReading(50f + i, 1f)
        }

        val result = speedCalculator.getFilteredSpeed()
        // Should only use last 5 readings
        assertTrue(result in 54f..60f)
    }

    @Test
    fun `handles_single_reading_edge_case`() {
        val result = speedCalculator.addReading(50f, 1f)

        // First reading should return itself
        assertEquals(50f, result, 0.1f)

        // Filtered speed should also be the same
        assertEquals(50f, speedCalculator.getFilteredSpeed(), 0.1f)
    }

    // ==================== Real-world Scenarios ====================

    @Test
    fun `simulates_city_driving_pattern`() {
        // Stop and go traffic pattern
        val speeds = listOf(0f, 15f, 30f, 25f, 10f, 0f, 5f, 20f, 35f, 30f)

        speeds.forEach { speed ->
            speedCalculator.addReading(speed, 2f)
        }

        val result = speedCalculator.getFilteredSpeed()
        // Should reflect recent higher speeds
        assertTrue(result in 15f..35f)
    }

    @Test
    fun `simulates_motorcycle_vs_car_speeds`() {
        // Motorcycle can accelerate faster but we should handle both
        // Car: 0-60 in 10 seconds = smooth readings
        val carSpeeds = listOf(0f, 6f, 12f, 18f, 24f, 30f, 36f, 42f, 48f, 54f, 60f)

        carSpeeds.forEach { speed ->
            speedCalculator.addReading(speed, 1f)
        }

        val result = speedCalculator.getFilteredSpeed()
        assertTrue(result in 40f..60f)
    }

    @Test
    fun `handles_GPS_signal_loss_and_recovery`() {
        // Good signal
        speedCalculator.addReading(50f, 1f)
        speedCalculator.addReading(51f, 1f)

        // Signal degraded (high accuracy value)
        speedCalculator.addReading(55f, 15f)

        // Signal recovered
        speedCalculator.addReading(52f, 1f)
        speedCalculator.addReading(50f, 1f)

        val result = speedCalculator.getFilteredSpeed()
        // Should not be heavily affected by single poor reading
        assertTrue(result in 48f..54f)
    }
}
