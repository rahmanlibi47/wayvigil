package com.saferoute.navigation

/** Confirms sustained, reliable route deviation before notifying the driver. */
class OffRouteDetector(
    private val confirmationFixes: Int = DEFAULT_CONFIRMATION_FIXES,
    private val maxFixGapMillis: Long = DEFAULT_MAX_FIX_GAP_MILLIS
) {
    private var consecutiveOffRouteFixes = 0
    private var lastFixTimestamp: Long? = null

    fun observe(result: RouteMatcher.MatchResult, timestamp: Long): Boolean {
        val previousTimestamp = lastFixTimestamp
        if (previousTimestamp != null && timestamp <= previousTimestamp) return false
        if (previousTimestamp != null && timestamp - previousTimestamp > maxFixGapMillis) {
            consecutiveOffRouteFixes = 0
        }
        lastFixTimestamp = timestamp

        when (result) {
            is RouteMatcher.MatchResult.OnRoute -> consecutiveOffRouteFixes = 0
            is RouteMatcher.MatchResult.OffRoute -> when (result.suggestedAction) {
                RouteMatcher.SuggestedAction.REROUTE -> consecutiveOffRouteFixes++
                RouteMatcher.SuggestedAction.CONTINUE -> consecutiveOffRouteFixes = 0
                RouteMatcher.SuggestedAction.CHECK_GPS -> return false
            }
        }
        return consecutiveOffRouteFixes >= confirmationFixes
    }

    fun reset() {
        consecutiveOffRouteFixes = 0
        lastFixTimestamp = null
    }

    companion object {
        private const val DEFAULT_CONFIRMATION_FIXES = 3
        private const val DEFAULT_MAX_FIX_GAP_MILLIS = 5_000L
    }
}
