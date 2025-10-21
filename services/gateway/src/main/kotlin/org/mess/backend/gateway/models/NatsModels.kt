// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/models/NatsModels.kt
package org.mess.backend.gateway.models.nats

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// Этот файл содержит data-классы, описывающие JSON-контракты
// для общения между gateway и ДРУГИМИ микросервисами через NATS.

// --- Модели для `auth-service` ---
@Serializable
data class NatsAuthRequest(val username: String, val password: String) // Запрос К auth-service (на auth.register/auth.login)
@Serializable
data class NatsAuthResponse(val token: String, val profile: NatsUserProfileStub) // Ответ ОТ auth-service
@Serializable
data class NatsUserProfileStub(val id: String, val username: String) // Заглушка профиля ОТ auth-service

// --- Модели для `user-service` ---
@Serializable
data class NatsProfileGetRequest(val userId: String) // Запрос К user-service (на user.profile.get)
@Serializable
data class NatsProfileUpdateRequest( // Запрос К user-service (на user.profile.update)
    val userId: String,
    val newUsername: String? = null,
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)
@Serializable
data class NatsSearchRequest(val query: String) // Запрос К user-service (на user.search)

@Serializable
data class NatsUserProfile( // Ответ ОТ user-service (ПОЛНАЯ модель профиля)
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)
@Serializable
data class NatsSearchResponse(val users: List<NatsUserProfile>) // Ответ ОТ user-service (на user.search)

// --- Модели для `chat-service` ---
// Запросы К chat-service
@Serializable
data class NatsIncomingMessage(val userId: String, val chatId: String, val type: String, val content: String) // Сообщение от клиента, публикуется В chat.message.incoming
@Serializable
data class NatsChatCreateGroupRequest(val creatorId: String, val name: String, val memberIds: List<String>) // Запрос на chat.create.group
@Serializable
data class NatsChatCreateDmRequest(val userId1: String, val userId2: String) // Запрос на chat.create.dm
@Serializable
data class NatsGetMyChatsRequest(val userId: String) // Запрос на chat.get.mychats
@Serializable
data class NatsAddUserToChatRequest(val addedByUserId: String, val chatId: String, val userIdToAdd: String) // Запрос на chat.member.add

// Ответы и События ОТ chat-service
@Serializable
data class NatsBroadcastMessage( // Сообщение для клиента, получается из NATS темы chat.broadcast.{userId}
    val messageId: String,
    val chatId: String,
    val sender: NatsUserProfile, // Включает ПОЛНЫЙ профиль отправителя
    val type: String,
    val content: String,
    val sentAt: Instant // kotlinx.datetime.Instant сериализуется в строку ISO 8601
)
@Serializable
data class NatsChat( // Ответ ОТ chat-service на запросы создания/обновления чата
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<NatsUserProfile> // Включает ПОЛНЫЕ профили участников
)
@Serializable
data class NatsGetMyChatsResponse(val chats: List<NatsChat>) // Ответ ОТ chat-service на chat.get.mychats