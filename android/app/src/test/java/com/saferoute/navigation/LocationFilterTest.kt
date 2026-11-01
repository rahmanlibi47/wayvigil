package com.saferoute.navigation

import com.saferoute.data.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocationFilterTest {

    private lateinit var locationFilter: LocationFilter

    @Before
    fun setup() {
        locationFilter = LocationFilter()
    }

    @Test
    fun `process accepts first location`() {
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 5f
        )

        val result = locationFilter.process(location)

        assertNotNull(result)
        assertEquals(location.latitude, result!!.latitude, 0.0001)
    }

    @Test
    fun `process rejects poor accuracy location`() {
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 100f // Too poor
        )

        val result = locationFilter.process(location)

        assertNull(result)
    }

    @Test
    fun `process filters micro movements`() {
        // First location
        val location1 = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 5f,
            speed = 0f
        )
        locationFilter.process(location1)

        // Very small movement while stationary
        val location2 = LocationData(
            latitude = 12.97161, // ~1 meter
            longitude = 77.59461,
            accuracy = 5f,
            speed = 0f
        )
        val result = locationFilter.process(location2)

        assertNull(result) // Should be filtered out
    }

    @Test
    fun `process accepts significant movement`() {
        // First location
        val location1 = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 5f,
            speed = 10f
        )
        locationFilter.process(location1)

        // Significant movement
        val location2 = LocationData(
            latitude = 12.9720, // ~50 meters
            longitude = 77.5950,
            accuracy = 5f,
            speed = 10f
        )
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `calculateDistance returns correct distance`() {
        val loc1 = LocationData(12.9716, 77.5946, 5f)
        val loc2 = LocationData(12.9720, 77.5950, 5f)

        val distance = locationFilter.calculateDistance(loc1, loc2)

        // Distance should be around 60-70 meters
        assertTrue(distance in 50f..100f)
    }

    @Test
    fun `calculateBearing returns correct bearing`() {
        val loc1 = LocationData(12.9716, 77.5946, 5f)
        val loc2 = LocationData(12.9816, 77.5946, 5f) // Due north

        val bearing = locationFilter.calculateBearing(loc1, loc2)

        // Bearing should be close to 0 (north)
        assertTrue(bearing in 0f..5f || bearing in 355f..360f)
    }

    @Test
    fun `reset clears filter state`() {
        val location = LocationData(12.9716, 77.5946, 5f)
        locationFilter.process(location)

        locationFilter.reset()

        // After reset, should accept new location as first
        val location2 = LocationData(12.9720, 77.5950, 5f)
        val result = locationFilter.process(location2)

        assertNotNull(result)
    }

    @Test
    fun `process rejects stale locations`() {
        val staleLocation = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 5f,
            timestamp = System.currentTimeMillis() - 10_000
        )

        assertNull(locationFilter.process(staleLocation))
    }

    @Test
    fun `process rejects coordinates outside valid ranges`() {
        val invalidLocation = LocationData(
            latitude = 91.0,
            longitude = 77.5946,
            accuracy = 5f
        )

        assertNull(locationFilter.process(invalidLocation))
    }
}
