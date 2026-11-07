package com.saferoute.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeadingEstimatorTest {
    @Test
    fun `uses reliable course while moving`() {
        val estimator = HeadingEstimator()
        estimator.updateDeviceHeading(90f, 1_000L)

        val heading = estimator.update(
            courseDegrees = 180f,
            courseAccuracyDegrees = 10f,
            speedMetersPerSecond = 5f,
            timestamp = 1_000L
        )

        assertEquals(180f, heading!!, 0.01f)
    }

    @Test
    fun `uses a fresh device heading when moving course is unreliable`() {
        val estimator = HeadingEstimator()
        estimator.updateDeviceHeading(90f, 1_000L)

        val heading = estimator.update(
            courseDegrees = 180f,
            courseAccuracyDegrees = 60f,
            speedMetersPerSecond = 5f,
            timestamp = 1_500L
        )

        assertEquals(90f, heading!!, 0.01f)
    }

    @Test
    fun `does not use stale device heading`() {
        val estimator = HeadingEstimator()
        estimator.updateDeviceHeading(90f, 1_000L)

        val heading = estimator.update(
            courseDegrees = null,
            courseAccuracyDegrees = null,
            speedMetersPerSecond = 0f,
            timestamp = 4_000L
        )

        assertNull(heading)
    }

    @Test
    fun `smooths heading across north using the short turn`() {
        val estimator = HeadingEstimator(smoothingFactor = 0.5f)
        estimator.update(359f, 5f, 5f, 1_000L)

        val heading = estimator.update(1f, 5f, 5f, 2_000L)

        assertEquals(0f, heading!!, 0.01f)
    }

    @Test
    fun `rejects invalid headings and course below movement threshold`() {
        val estimator = HeadingEstimator()

        val heading = estimator.update(
            courseDegrees = Float.NaN,
            courseAccuracyDegrees = 5f,
            speedMetersPerSecond = 1f,
            timestamp = 1_000L
        )

        assertNull(heading)
    }

    @Test
    fun `accepts course at the configured speed and accuracy boundaries`() {
        val estimator = HeadingEstimator()

        val heading = estimator.update(
            courseDegrees = 45f,
            courseAccuracyDegrees = HeadingEstimator.MAXIMUM_COURSE_ACCURACY_DEGREES,
            speedMetersPerSecond = HeadingEstimator.MINIMUM_COURSE_SPEED_METERS_PER_SECOND,
            timestamp = 1_000L
        )

        assertEquals(45f, heading!!, 0.01f)
    }

    @Test
    fun `ignores out of order device heading updates`() {
        val estimator = HeadingEstimator(smoothingFactor = 1f)
        estimator.updateDeviceHeading(90f, 2_000L)
        estimator.updateDeviceHeading(180f, 1_000L)

        val heading = estimator.update(
            courseDegrees = null,
            courseAccuracyDegrees = null,
            speedMetersPerSecond = 0f,
            timestamp = 2_000L
        )

        assertEquals(90f, heading!!, 0.01f)
    }

    @Test
    fun `clearing device heading leaves no stale fallback`() {
        val estimator = HeadingEstimator()
        estimator.updateDeviceHeading(90f, 1_000L)
        estimator.update(null, null, 0f, 1_000L)

        estimator.clearDeviceHeading()

        assertNull(estimator.update(null, null, 0f, 1_100L))
    }

    @Test
    fun `reset clears smoothed and device heading state`() {
        val estimator = HeadingEstimator(smoothingFactor = 1f)
        estimator.updateDeviceHeading(120f, 1_000L)
        estimator.update(null, null, 0f, 1_000L)

        estimator.reset()

        assertNull(estimator.update(null, null, 0f, 1_100L))
    }
}
