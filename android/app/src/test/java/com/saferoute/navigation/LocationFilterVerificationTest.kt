package com.saferoute.navigation

import com.saferoute.data.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive verification tests for LocationFilter.
 * Tests walking speeds, slow vehicle movement, stationary drift, poor accuracy,
 * and location jumps.
 */
class LocationFilterVerificationTest {

    private lateinit var locationFilter: LocationFilter

    @Before
    fun setup() {
        locationFilter = LocationFilter()
    }

    // ==================== Walking Speeds ====================

    @Test
    fun `accepts_normal_walking_speed_movement`() {
        // Average walking speed ~5 km/h = ~1.4 m/s
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 1.4f)
        locationFilter.process(location1)

        // 5 meters in ~3.5 seconds at walking speed
        val location2 = LocationData(12.971645, 77.594600, 5f, speed = 1.4f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `accepts_slow_walking_speed`() {
        // Slow walking ~3 km/h = ~0.8 m/s
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 0.8f)
        locationFilter.process(location1)

        // Small movement
        val location2 = LocationData(12.971630, 77.594600, 5f, speed = 0.8f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `accepts_brisk_walking_speed`() {
        // Brisk walking ~6 km/h = ~1.7 m/s
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 1.7f)
        locationFilter.process(location1)

        val location2 = LocationData(12.971655, 77.594600, 5f, speed = 1.7f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    // ==================== Slow Vehicle Movement ====================

    @Test
    fun `accepts_slow_traffic_movement`() {
        // Traffic jam ~10 km/h = ~2.8 m/s
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 2.8f)
        locationFilter.process(location1)

        val location2 = LocationData(12.971625, 77.594600, 5f, speed = 2.8f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `accepts_parking_lot_speeds`() {
        // Parking lot ~5 km/h = ~1.4 m/s
        val location1 = LocationData(12.971600, 77.594600, 8f, speed = 1.4f)
        locationFilter.process(location1)

        val location2 = LocationData(12.971615, 77.594600, 8f, speed = 1.4f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `accepts_motorcycle_in_traffic`() {
        // Motorcycle filtering through traffic
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 4.2f) // ~15 km/h
        locationFilter.process(location1)

        val location2 = LocationData(12.971640, 77.594600, 5f, speed = 4.2f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    // ==================== Stationary GPS Drift ====================

    @Test
    fun `filters_stationary_drift_under_3_meters`() {
        // User is stationary but GPS shows micro-movement
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 0f)
        locationFilter.process(location1)

        // 2 meter drift (should be filtered)
        val location2 = LocationData(12.971618, 77.594600, 5f, speed = 0f)
        val result = locationFilter.process(location2)

        assertNull(result)
    }

    @Test
    fun `filters_multiple_stationary_readings`() {
        // Simulate GPS drift while stationary
        val baseLat = 12.971600
        val baseLon = 77.594600

        val drifts = listOf(
            Pair(0.0, 0.0),       // Starting point
            Pair(0.00001, 0.0),   // ~1m drift
            Pair(0.00002, 0.00001), // ~2.5m drift
            Pair(-0.00001, 0.0),  // Back toward start
            Pair(0.0, 0.00002),   // Different direction
        )

        var acceptedCount = 0
        drifts.forEach { (latOffset, lonOffset) ->
            val location = LocationData(
                baseLat + latOffset,
                baseLon + lonOffset,
                5f,
                speed = 0f
            )
            if (locationFilter.process(location) != null) {
                acceptedCount++
            }
        }

        // Most drifts should be filtered
        assertTrue(acceptedCount <= 2)
    }

    @Test
    fun `accepts_movement_when_reporting_low_speed_but_large_displacement`() {
        // GPS reports stationary but actual movement is clear
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 0f)
        locationFilter.process(location1)

        // 20 meters movement (should be accepted even with 0 speed)
        val location2 = LocationData(12.971780, 77.594600, 5f, speed = 0f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    // ==================== Poor Accuracy ====================

    @Test
    fun `rejects_very_poor_accuracy_100m`() {
        val location = LocationData(12.971600, 77.594600, 100f)
        val result = locationFilter.process(location)

        assertNull(result)
    }

    @Test
    fun `rejects_poor_accuracy_60m`() {
        val location = LocationData(12.971600, 77.594600, 60f)
        val result = locationFilter.process(location)

        assertNull(result)
    }

    @Test
    fun `accepts_borderline_accuracy_50m`() {
        // Exactly at threshold (upper limit)
        val location = LocationData(12.971600, 77.594600, 50f)
        val result = locationFilter.process(location)

        // Should be rejected (accuracy must be < 50m)
        assertNull(result)
    }

    @Test
    fun `accepts_good_accuracy_10m`() {
        val location = LocationData(12.971600, 77.594600, 10f)
        val result = locationFilter.process(location)

        assertNotNull(result)
    }

    @Test
    fun `accepts_excellent_accuracy_3m`() {
        val location = LocationData(12.971600, 77.594600, 3f)
        val result = locationFilter.process(location)

        assertNotNull(result)
    }

    @Test
    fun `accepts_varied_accuracy_sequence`() {
        // Sequence of varying accuracy
        val locations = listOf(
            LocationData(12.971600, 77.594600, 5f),
            LocationData(12.971650, 77.594600, 8f),
            LocationData(12.971700, 77.594600, 15f),  // Poorer
            LocationData(12.971750, 77.594600, 6f),
            LocationData(12.971800, 77.594600, 4f),
        )

        var acceptedCount = 0
        locations.forEach { location ->
            if (locationFilter.process(location) != null) {
                acceptedCount++
            }
        }

        // At least 3 should be accepted (the ones with < 50m accuracy and sufficient movement)
        assertTrue(acceptedCount >= 3)
    }

    // ==================== Sudden Location Jumps ====================

    @Test
    fun `handles_reasonable_movement_between_readings`() {
        val location1 = LocationData(12.971600, 77.594600, 5f, speed = 13.9f) // 50 km/h
        locationFilter.process(location1)

        // 28 meters in 2 seconds = ~50 km/h (reasonable)
        val location2 = LocationData(12.971850, 77.594600, 5f, speed = 13.9f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `handles_large_jump_with_stale_timestamp`() {
        val location1 = LocationData(
            12.971600, 77.594600, 5f,
            timestamp = System.currentTimeMillis() - 10000 // 10 seconds ago
        )
        locationFilter.process(location1)

        // 500 meter jump but over 10 seconds = reasonable
        val location2 = LocationData(
            12.976100, 77.594600, 5f,
            timestamp = System.currentTimeMillis()
        )
        val result = locationFilter.process(location2)

        // Should accept due to stale previous reading
        assertNotNull(result)
    }

    // ==================== Distance Calculations ====================

    @Test
    fun `calculates_distance_for_1_meter_correctly`() {
        val loc1 = LocationData(12.971600, 77.594600, 5f)
        val loc2 = LocationData(12.971609, 77.594600, 5f) // ~1m north

        val distance = locationFilter.calculateDistance(loc1, loc2)

        assertTrue(distance in 0.5f..1.5f)
    }

    @Test
    fun `calculates_distance_for_100_meters_correctly`() {
        val loc1 = LocationData(12.971600, 77.594600, 5f)
        val loc2 = LocationData(12.972500, 77.594600, 5f) // ~100m north

        val distance = locationFilter.calculateDistance(loc1, loc2)

        assertTrue(distance in 95f..105f)
    }

    @Test
    fun `calculates_distance_for_1_km_correctly`() {
        val loc1 = LocationData(12.971600, 77.594600, 5f)
        val loc2 = LocationData(12.980600, 77.594600, 5f) // ~1km north

        val distance = locationFilter.calculateDistance(loc1, loc2)

        assertTrue(distance in 950f..1050f)
    }

    @Test
    fun `calculates_diagonal_distance_correctly`() {
        val loc1 = LocationData(12.971600, 77.594600, 5f)
        val loc2 = LocationData(12.972300, 77.595300, 5f) // NE direction

        val distance = locationFilter.calculateDistance(loc1, loc2)

        // Should be around 110 meters (diagonal of ~100m x ~70m)
        assertTrue(distance in 100f..120f)
    }

    // ==================== Bearing Calculations ====================

    @Test
    fun `calculates_north_bearing_correctly`() {
        val loc1 = LocationData(12.971600, 77.594600, 5f)
        val loc2 = LocationData(12.981600, 77.594600, 5f) // Due north

        val bearing = locationFilter.calculateBearing(loc1, loc2)

        assertTrue(bearing in 0f..5f || bearing in 355f..360f)
    }

    @Test
    fun `calculates_east_bearing_correctly`() {
        val loc1 = LocationData(12.971600, 77.594600, 5f)
        val loc2 = LocationData(12.971600, 77.604600, 5f) // Due east

        val bearing = locationFilter.calculateBearing(loc1, loc2)

        assertTrue(bearing in 85f..95f)
    }

    @Test
    fun `calculates_south_bearing_correctly`() {
        val loc1 = LocationData(12.981600, 77.594600, 5f)
        val loc2 = LocationData(12.971600, 77.594600, 5f) // Due south

        val bearing = locationFilter.calculateBearing(loc1, loc2)

        assertTrue(bearing in 175f..185f)
    }

    @Test
    fun `calculates_west_bearing_correctly`() {
        val loc1 = LocationData(12.971600, 77.604600, 5f)
        val loc2 = LocationData(12.971600, 77.594600, 5f) // Due west

        val bearing = locationFilter.calculateBearing(loc1, loc2)

        assertTrue(bearing in 265f..275f)
    }

    // ==================== Edge Cases ====================

    @Test
    fun `handles_zero_accuracy`() {
        val location = LocationData(12.971600, 77.594600, 0f)
        val result = locationFilter.process(location)

        assertNotNull(result)
    }

    @Test
    fun `handles_negative_coordinates`() {
        val location1 = LocationData(-12.971600, -77.594600, 5f)
        val result1 = locationFilter.process(location1)

        assertNotNull(result1)

        val location2 = LocationData(-12.972600, -77.594600, 5f)
        val result2 = locationFilter.process(location2)

        assertNotNull(result2)
    }

    @Test
    fun `reset_clears_internal_state`() {
        // Process some locations
        locationFilter.process(LocationData(12.971600, 77.594600, 5f))
        locationFilter.process(LocationData(12.971700, 77.594600, 5f))

        // Reset
        locationFilter.reset()

        // First location after reset should be accepted
        val result = locationFilter.process(LocationData(12.972000, 77.594600, 5f))
        assertNotNull(result)
    }

    @Test
    fun `handles_rapid_successive_movements`() {
        // Simulate rapid location updates (e.g., high-frequency GPS)
        val baseLat = 12.971600
        val baseLon = 77.594600

        repeat(10) { i ->
            val location = LocationData(
                baseLat + (i * 0.0001), // 10m each step
                baseLon,
                3f
            )
            val result = locationFilter.process(location)
            // Most should be accepted as they're > 3m
            if (i > 0) {
                assertNotNull(result)
            }
        }
    }
}
