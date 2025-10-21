package org.mess.backend.auth.models

import kotlinx.serialization.Serializable

// Этот файл содержит data-классы, описывающие JSON-контракты
// для общения этого сервиса (auth) с другими через NATS.

// Запрос от Gateway -> AuthService (на темы auth.register, auth.login)
@Serializable
data class NatsAuthRequest(
    val username: String,
    val password: String
)


// Ответ от AuthService -> Gateway (успешный)
// Содержит ТОЛЬКО ID и username, т.к. остальное (ник, аватар и т.д.) - в user-service
@Serializable
data class NatsAuthResponse(
    val token: String,
    val profile: NatsUserProfileStub // Используем "заглушку" профиля
)

// Заглушка профиля, возвращаемая auth-service в NatsAuthResponse
@Serializable
data class NatsUserProfileStub(
    val id: String, // UUID пользователя
    val username: String
)


// Событие (Event), публикуемое AuthService -> UserService (в тему user.created)
@Serializable
internal data class UserCreatedEvent(
    val userId: String, // UUID нового пользователя
    val username: String
)