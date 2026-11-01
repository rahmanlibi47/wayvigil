package com.saferoute.data.model

import kotlinx.serialization.Serializable

enum class HazardType {
    SPEED_BREAKER,
    POTHOLE,
    SHARP_CURVE,
    DANGEROUS_INTERSECTION,
    ROAD_CONSTRUCTION,
    ACCIDENT,
    WATERLOGGING,
    ROAD_OBSTRUCTION,
    DAMAGED_ROAD,
    RAILWAY_CROSSING,
    OTHER
}

enum class HazardStatus {
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED,
    EXPIRED
}

enum class HazardSeverity {
    LOW,
    MODERATE,
    HIGH,
    CRITICAL
}

@Serializable
data class Location(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float? = null
)

@Serializable
data class Hazard(
    val id: String,
    val hazardType: String,
    val status: String,
    val severity: String,
    val location: Location,
    val description: String? = null,
    val firstReportedAt: String? = null,
    val lastReportedAt: String? = null,
    val reportCount: Int = 1,
    val confirmationCount: Int = 0,
    val expiresAt: String? = null
)

@Serializable
data class HazardReport(
    val id: String,
    val hazardId: String? = null,
    val hazardType: String,
    val status: String,
    val location: Location,
    val description: String? = null,
    @kotlinx.serialization.SerialName("media_urls")
    val mediaUrls: List<String>? = null,
    val createdAt: String
)

@Serializable
data class CreateHazardReportRequest(
    val hazardType: String,
    val description: String? = null,
    val location: Location,
    @kotlinx.serialization.SerialName("media_urls")
    val mediaUrls: List<String>? = null,
    val deviceId: String? = null,
    val appVersion: String? = null
)

@Serializable
data class NearbyHazardRequest(
    val latitude: Double,
    val longitude: Double,
    val radius: Double = 500.0,
    val includeUnverified: Boolean = false,
    val hazardTypes: List<String>? = null
)
