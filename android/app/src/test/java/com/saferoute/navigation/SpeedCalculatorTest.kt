package com.saferoute.navigation

import com.saferoute.data.model.LocationData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SpeedCalculatorTest {

    private lateinit var speedCalculator: SpeedCalculator

    @Before
    fun setup() {
        speedCalculator = SpeedCalculator()
    }

    @Test
    fun `addReading returns same speed for first reading`() {
        val speed = 50f
        val accuracy = 1f

        val result = speedCalculator.addReading(speed, accuracy)

        assertEquals(speed, result, 0.1f)
    }

    @Test
    fun `addReading filters sudden speed jumps`() {
        // Add initial readings to establish baseline
        speedCalculator.addReading(50f, 1f)
        speedCalculator.addReading(51f, 1f)
        speedCalculator.addReading(50f, 1f)

        // Try to add a sudden jump
        val result = speedCalculator.addReading(100f, 5f)

        // Should be filtered out (rejected due to large change with poor accuracy)
        assertTrue(result < 80f)
    }

    @Test
    fun `addReading accepts large change with good accuracy`() {
        // Add initial readings
        speedCalculator.addReading(50f, 1f)
        speedCalculator.addReading(51f, 1f)

        // Large change but good accuracy should be accepted
        val result = speedCalculator.addReading(100f, 0.5f)

        assertTrue(result > 70f)
    }

    @Test
    fun `getFilteredSpeed returns weighted average`() {
        // Add multiple readings with varying accuracy
        speedCalculator.addReading(50f, 1f) // High quality
        speedCalculator.addReading(52f, 1f) // High quality
        speedCalculator.addReading(48f, 3f) // Lower quality
        speedCalculator.addReading(51f, 1f) // High quality
        speedCalculator.addReading(49f, 1f) // High quality

        val result = speedCalculator.getFilteredSpeed()

        // Should be around 50, weighted toward high-quality readings
        assertTrue(result in 49f..52f)
    }

    @Test
    fun `addLocation extracts speed from location data`() {
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 5f,
            speed = 13.89f, // ~50 km/h in m/s
            speedAccuracy = 0.5f
        )

        val result = speedCalculator.addLocation(location)

        // Should convert m/s to km/h (13.89 * 3.6 = ~50)
        assertTrue(result in 49f..51f)
    }

    @Test
    fun `addLocation returns last valid speed when GPS speed unavailable`() {
        // Add initial valid reading
        speedCalculator.addReading(50f, 1f)

        // Location without valid speed
        val location = LocationData(
            latitude = 12.9716,
            longitude = 77.5946,
            accuracy = 5f,
            speed = 0f,
            speedAccuracy = 10f // Poor accuracy
        )

        val result = speedCalculator.addLocation(location)

        // Should return last valid filtered speed
        assertTrue(result > 0f)
    }

    @Test
    fun `clear resets calculator state`() {
        speedCalculator.addReading(50f, 1f)
        speedCalculator.addReading(51f, 1f)

        speedCalculator.clear()

        val result = speedCalculator.getFilteredSpeed()
        assertEquals(0f, result, 0.1f)
    }
}
