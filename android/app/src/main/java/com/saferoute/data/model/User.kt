
package com.saferoute.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class User(
    val id: String,
    val email: String? = null,

    @SerialName("phone_number")
    val phoneNumber: String? = null,

    @SerialName("display_name")
    val displayName: String? = null,

    @SerialName("photo_url")
    val photoUrl: String? = null,

    @SerialName("vehicle_type")
    val vehicleType: String = "car",

    @SerialName("preferred_language")
    val preferredLanguage: String = "en",

    @SerialName("is_active")
    val isActive: Boolean = true,

    @SerialName("is_anonymous")
    val isAnonymous: Boolean = false,

    @SerialName("created_at")
    val createdAt: String? = null,

    @SerialName("updated_at")
    val updatedAt: String? = null,

    @SerialName("last_login_at")
    val lastLoginAt: String? = null
)

@Serializable
data class UserUpdateRequest(
    @SerialName("display_name")
    val displayName: String? = null,

    @SerialName("vehicle_type")
    val vehicleType: String? = null,

    @SerialName("preferred_language")
    val preferredLanguage: String? = null,

    @SerialName("photo_url")
    val photoUrl: String? = null
)
