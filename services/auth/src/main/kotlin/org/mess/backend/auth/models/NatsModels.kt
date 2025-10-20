package org.mess.backend.auth.models

import kotlinx.serialization.Serializable

// Запрос от Gateway -> AuthService
@Serializable
data class NatsAuthRequest(
    val username: String,
    val password: String
)

// Ответ от AuthService -> Gateway
@Serializable
data class NatsAuthResponse(
    val token: String,
    val profile: NatsUserProfile // (Auth-service создает "профиль" при регистрации)
)

// Упрощенный профиль (без id)
@Serializable
data class NatsUserProfile(
    val username: String // При регистрации nickname = username
)

// Событие (Event) от AuthService -> UserService
@Serializable
data class UserCreatedEvent(
    val userId: String,
    val username: String
)

// Ответ с ошибкой (для Request-Reply)
@Serializable
data class NatsErrorResponse(
    val error: String
)