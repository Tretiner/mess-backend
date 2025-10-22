// FILE: services/user/src/main/kotlin/org/mess/backend/user/models/NatsModels.kt
package org.mess.backend.user.models

import kotlinx.serialization.Serializable
import org.mess.backend.core.NatsErrorResponse // Assuming shared core module

// --- События (Слушаем) ---
@Serializable
data class UserCreatedEvent(
    val userId: String,
    val username: String // Приходит от auth-service
)

// --- Запросы (Слушаем) ---
@Serializable
data class NatsProfileGetRequest(
    val userId: String
)

@Serializable
data class NatsProfileUpdateRequest(
    val userId: String,
    val newUsername: String? = null, // <-- RENAMED from newNickname
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)

@Serializable
data class NatsSearchRequest(
    val query: String // Search term
)

// --- Ответы (Отправляем) ---
@Serializable
data class NatsUserProfile( // The canonical user profile model
    val id: String,
    val username: String, // <-- RENAMED from nickname
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

@Serializable
data class NatsSearchResponse(
    val users: List<NatsUserProfile> // Contains full profiles
)

// NatsErrorResponse is now likely in the core module
// @Serializable data class NatsErrorResponse(val error: String)