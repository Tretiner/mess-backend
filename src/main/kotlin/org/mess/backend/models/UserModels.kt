package org.mess.backend.models

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val id: String,
    val nickname: String,
    val avatarUrl: String?
)

@Serializable
data class UserProfileUpdateRequest(
    val newNickname: String?,
    val newAvatarUrl: String?
)