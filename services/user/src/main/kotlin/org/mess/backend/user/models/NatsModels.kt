package org.mess.backend.user.models

import kotlinx.serialization.Serializable

// ---
// МОДЕЛИ СОБЫТИЙ (Events) - то, что мы СЛУШАЕМ
// ---

// Событие, которое публикует `auth-service`
@Serializable
data class UserCreatedEvent(
    val userId: String,
    val username: String
)

// ---
// МОДЕЛИ ЗАПРОСОВ (Requests) - то, что мы СЛУШАЕМ
// ---

// Запрос от Gateway на `user.profile.get`
@Serializable
data class NatsProfileGetRequest(
    val userId: String
)

// Запрос от Gateway на `user.profile.update`
@Serializable
data class NatsProfileUpdateRequest(
    val userId: String, // ID пользователя, чей профиль обновляем (из JWT)
    val newNickname: String?,
    val newAvatarUrl: String?
)

// Запрос от Gateway на `user.search`
@Serializable
data class NatsSearchRequest(
    val query: String
)

// ---
// МОДЕЛИ ОТВЕТОВ (Replies) - то, что мы ОТПРАВЛЯЕМ
// ---

// Каноничная модель профиля
@Serializable
data class NatsUserProfile(
    val id: String,
    val nickname: String,
    val avatarUrl: String?
)

// Ответ на `user.search`
@Serializable
data class NatsSearchResponse(
    val users: List<NatsUserProfile>
)

// Ответ с ошибкой
@Serializable
data class NatsErrorResponse(
    val error: String
)