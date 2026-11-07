package com.saferoute.navigation

/** Selects a reliable heading source and smooths changes across north. */
class HeadingEstimator(
    private val minimumCourseSpeedMetersPerSecond: Float = MINIMUM_COURSE_SPEED_METERS_PER_SECOND,
    private val maximumCourseAccuracyDegrees: Float = MAXIMUM_COURSE_ACCURACY_DEGREES,
    private val deviceHeadingMaxAgeMs: Long = DEVICE_HEADING_MAX_AGE_MS,
    private val smoothingFactor: Float = SMOOTHING_FACTOR
) {
    private var deviceHeading: Float? = null
    private var deviceHeadingTimestamp: Long = 0L
    private var smoothedHeading: Float? = null

    init {
        require(smoothingFactor in 0f..1f)
    }

    fun updateDeviceHeading(headingDegrees: Float, timestamp: Long) {
        if (
            headingDegrees.isFinite() && timestamp > 0L &&
            timestamp >= deviceHeadingTimestamp
        ) {
            deviceHeading = normalize(headingDegrees)
            deviceHeadingTimestamp = timestamp
        }
    }

    fun update(
        courseDegrees: Float?,
        courseAccuracyDegrees: Float?,
        speedMetersPerSecond: Float,
        timestamp: Long
    ): Float? {
        val courseIsReliable = courseDegrees != null && courseDegrees.isFinite() &&
            speedMetersPerSecond.isFinite() &&
            speedMetersPerSecond >= minimumCourseSpeedMetersPerSecond &&
            courseAccuracyDegrees != null && courseAccuracyDegrees.isFinite() &&
            courseAccuracyDegrees in 0f..maximumCourseAccuracyDegrees

        val deviceIsFresh = deviceHeading != null &&
            timestamp >= deviceHeadingTimestamp &&
            timestamp - deviceHeadingTimestamp <= deviceHeadingMaxAgeMs
        val candidate = when {
            courseIsReliable -> normalize(courseDegrees!!)
            deviceIsFresh -> deviceHeading
            else -> null
        } ?: return null

        val previous = smoothedHeading
        smoothedHeading = if (previous == null) {
            candidate
        } else {
            normalize(previous + shortestTurn(previous, candidate) * smoothingFactor)
        }
        return smoothedHeading
    }

    fun reset() {
        deviceHeading = null
        deviceHeadingTimestamp = 0L
        smoothedHeading = null
    }

    fun clearDeviceHeading() {
        deviceHeading = null
        deviceHeadingTimestamp = 0L
    }

    private fun normalize(degrees: Float): Float =
        (degrees % FULL_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE

    private fun shortestTurn(from: Float, to: Float): Float =
        ((to - from + HALF_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE) - HALF_CIRCLE

    companion object {
        const val MINIMUM_COURSE_SPEED_METERS_PER_SECOND = 2f
        const val MAXIMUM_COURSE_ACCURACY_DEGREES = 45f
        const val DEVICE_HEADING_MAX_AGE_MS = 2_000L
        const val SMOOTHING_FACTOR = 0.65f
        private const val FULL_CIRCLE = 360f
        private const val HALF_CIRCLE = 180f
    }
}
