// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/models/NatsModels.kt
package org.mess.backend.gateway.models.nats

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// Этот файл содержит data-классы, описывающие JSON-контракты
// для общения между gateway и другими микросервисами через NATS.

// --- Общие ---
@Serializable
data class NatsErrorResponse(val error: String) // Модель для ответа с ошибкой от любого сервиса

// --- Модели для `auth-service` ---
@Serializable
data class NatsAuthRequest(val username: String, val password: String) // Запрос на auth.register/auth.login
@Serializable
data class NatsAuthResponse(val token: String, val profile: NatsUserProfile) // Успешный ответ от auth.register/auth.login

// --- Модели для `user-service` ---
@Serializable
data class NatsProfileGetRequest(val userId: String) // Запрос на user.profile.get
@Serializable
data class NatsProfileUpdateRequest(val userId: String, val newNickname: String?, val newAvatarUrl: String?) // Запрос на user.profile.update
@Serializable
data class NatsSearchRequest(val query: String) // Запрос на user.search

@Serializable
data class NatsUserProfile(val id: String, val nickname: String, val avatarUrl: String?) // Ответ от user.profile.get/update, используется и в других моделях
@Serializable
data class NatsSearchResponse(val users: List<NatsUserProfile>) // Ответ от user.search

// --- Модели для `chat-service` ---
// Запросы к chat-service
@Serializable
data class NatsIncomingMessage(val userId: String, val chatId: String, val type: String, val content: String) // Публикуется в chat.message.incoming
@Serializable
data class NatsChatCreateGroupRequest(val creatorId: String, val name: String, val memberIds: List<String>) // Запрос на chat.create.group
@Serializable
data class NatsChatCreateDmRequest(val userId1: String, val userId2: String) // Запрос на chat.create.dm
@Serializable
data class NatsGetMyChatsRequest(val userId: String) // Запрос на chat.get.mychats
@Serializable
data class NatsAddUserToChatRequest(val addedByUserId: String, val chatId: String, val userIdToAdd: String) // Запрос на chat.member.add

// Ответы и события от chat-service
@Serializable
data class NatsBroadcastMessage( // Публикуется в chat.broadcast.{userId}
    val messageId: String,
    val chatId: String,
    val sender: NatsUserProfile, // Включает инфо об отправителе из user-service
    val type: String,
    val content: String,
    val sentAt: Instant // kotlinx.datetime.Instant сериализуется в строку ISO 8601
)
@Serializable
data class NatsChat( // Ответ от chat.create.group/dm, chat.member.add
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<NatsUserProfile> // Включает инфо об участниках из user-service
)
@Serializable
data class NatsGetMyChatsResponse(val chats: List<NatsChat>) // Ответ от chat.get.mychats