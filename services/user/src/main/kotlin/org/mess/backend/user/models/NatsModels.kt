package org.mess.backend.user.models

import kotlinx.serialization.Serializable

// --- События (Слушаем) ---
@Serializable
data class UserCreatedEvent(
    val userId: String,
    val username: String
)

// --- Запросы (Слушаем) ---
@Serializable
data class NatsProfileGetRequest(
    val userId: String
)

// --- НОВЫЙ ЗАПРОС ---
@Serializable
data class NatsProfilesGetBatchRequest(
    val userIds: List<String>
)

@Serializable
data class NatsProfileUpdateRequest(
    val userId: String,
    val newUsername: String? = null,
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)

@Serializable
data class NatsSearchRequest(
    val query: String
)

// --- Ответы (Отправляем) ---
@Serializable
data class NatsUserProfile(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

// --- НОВЫЙ ОТВЕТ ---
@Serializable
data class NatsProfilesGetBatchResponse(
    val profiles: List<NatsUserProfile>
)

@Serializable
data class NatsSearchResponse(
    val users: List<NatsUserProfile>
)