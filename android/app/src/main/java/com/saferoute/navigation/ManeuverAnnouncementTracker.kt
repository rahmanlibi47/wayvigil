package com.saferoute.navigation

import com.saferoute.data.model.RouteStep

/** Produces one approach and one immediate spoken prompt for each route step. */
class ManeuverAnnouncementTracker {
    private var activeStep = -1
    private val approachingSteps = mutableSetOf<Int>()
    private val announcedSteps = mutableSetOf<Int>()

    fun nextAnnouncement(
        currentStep: Int,
        distanceToNextManeuverMeters: Int,
        steps: List<RouteStep>
    ): String? {
        if (steps.isEmpty()) return null
        val stepIndex = currentStep.coerceIn(0, steps.lastIndex)
        if (activeStep == -1) {
            activeStep = stepIndex
            announcedSteps += stepIndex
            return steps[stepIndex].instruction.takeIf(String::isNotBlank)
        }

        if (stepIndex != activeStep) {
            activeStep = stepIndex
            if (announcedSteps.add(stepIndex)) {
                return "Now, ${steps[stepIndex].instruction}"
            }
        }

        val nextStep = stepIndex + 1
        if (nextStep !in steps.indices || steps[nextStep].instruction.isBlank()) return null
        if (distanceToNextManeuverMeters <= IMMEDIATE_ANNOUNCEMENT_METERS) {
            if (announcedSteps.add(nextStep)) {
                return "Now, ${steps[nextStep].instruction}"
            }
        } else if (distanceToNextManeuverMeters <= APPROACH_ANNOUNCEMENT_METERS &&
            approachingSteps.add(nextStep)
        ) {
            val roundedDistance = ((distanceToNextManeuverMeters + 25) / 50) * 50
            return "In $roundedDistance meters, ${steps[nextStep].instruction}"
        }
        return null
    }

    fun reset() {
        activeStep = -1
        approachingSteps.clear()
        announcedSteps.clear()
    }

    companion object {
        private const val APPROACH_ANNOUNCEMENT_METERS = 300
        private const val IMMEDIATE_ANNOUNCEMENT_METERS = 50
    }
}
