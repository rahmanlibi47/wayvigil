package com.saferoute.navigation

import com.saferoute.data.model.Route

/** Tracks monotonic progress and confirms arrival near the route endpoint. */
class RouteProgressTracker(route: Route) {
    private val routeDistanceMeters = route.distanceMeters.coerceAtLeast(0)
    private val routeDurationSeconds = route.durationSeconds.coerceAtLeast(0)
    private val stepDistances = route.steps.map { it.distanceMeters.coerceAtLeast(0) }
    private val totalStepDistanceMeters = stepDistances.sum()
    private var furthestProgressMeters = 0

    data class Progress(
        val remainingDistanceMeters: Int,
        val remainingDurationSeconds: Int,
        val currentStep: Int,
        val distanceToNextManeuverMeters: Int,
        val arrived: Boolean
    )

    fun update(progressFraction: Float, distanceToDestinationMeters: Float): Progress {
        val safeFraction = progressFraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0f
        val measuredProgressMeters = (routeDistanceMeters * safeFraction).toInt()
        furthestProgressMeters = maxOf(furthestProgressMeters, measuredProgressMeters)

        val remainingDistance = (routeDistanceMeters - furthestProgressMeters).coerceAtLeast(0)
        val remainingDuration = if (routeDistanceMeters > 0) {
            (routeDurationSeconds * (remainingDistance.toFloat() / routeDistanceMeters)).toInt()
        } else {
            0
        }
        val minimumArrivalProgressMeters =
            (routeDistanceMeters * MIN_ARRIVAL_PROGRESS_FRACTION).toInt()
        val stepProgressMeters = if (routeDistanceMeters > 0) {
            totalStepDistanceMeters * (furthestProgressMeters.toFloat() / routeDistanceMeters)
        } else {
            0f
        }
        var distanceThroughSteps = 0f
        var currentStep = 0
        var distanceToNextManeuver = 0
        for (index in stepDistances.indices) {
            distanceThroughSteps += stepDistances[index]
            if (stepProgressMeters < distanceThroughSteps || index == stepDistances.lastIndex) {
                currentStep = index
                distanceToNextManeuver = (distanceThroughSteps - stepProgressMeters)
                    .toInt()
                    .coerceAtLeast(0)
                break
            }
        }

        return Progress(
            remainingDistanceMeters = remainingDistance,
            remainingDurationSeconds = remainingDuration,
            currentStep = currentStep,
            distanceToNextManeuverMeters = distanceToNextManeuver,
            arrived = furthestProgressMeters >= minimumArrivalProgressMeters &&
                distanceToDestinationMeters.isFinite() &&
                distanceToDestinationMeters in 0f..ARRIVAL_THRESHOLD_METERS
        )
    }

    companion object {
        private const val MIN_ARRIVAL_PROGRESS_FRACTION = 0.9f
        private const val ARRIVAL_THRESHOLD_METERS = 35f
    }
}
