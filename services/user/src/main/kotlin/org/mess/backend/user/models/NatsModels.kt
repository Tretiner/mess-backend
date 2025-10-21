package org.mess.backend.user.models

import kotlinx.serialization.Serializable

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
    // --- НОВЫЕ ПОЛЯ ---
    val newNickname: String? = null,
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---
)

@Serializable
data class NatsSearchRequest(
    val query: String
)

// --- Ответы (Отправляем) ---
@Serializable
data class NatsUserProfile(
    val id: String,
    // --- НОВЫЕ ПОЛЯ ---
    val nickname: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---
)

@Serializable
data class NatsSearchResponse(
    val users: List<NatsUserProfile>
)