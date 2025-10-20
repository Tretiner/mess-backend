// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/models/ApiModels.kt
package org.mess.backend.gateway.models.api

import kotlinx.serialization.Serializable
import org.mess.backend.gateway.models.*
import org.mess.backend.gateway.models.nats.*

// --- Модели для REST API-запросов (от клиента) ---

@Serializable
data class AuthApiRequest(val username: String, val password: String)

@Serializable
data class UpdateProfileApiRequest(val newNickname: String? = null, val newAvatarUrl: String? = null) // Поля опциональны

@Serializable
data class CreateGroupChatApiRequest(val name: String, val memberIds: List<String>)

// Сообщение, которое клиент (например, Android) шлет в WebSocket
@Serializable
data class ChatMessageApiRequest(val chatId: String, val type: String, val content: String)


// --- Модели для REST API-ответов (клиенту) ---

@Serializable
data class AuthApiResponse(val token: String, val profile: UserProfileApiResponse)

@Serializable
data class UserProfileApiResponse(val id: String, val nickname: String, val avatarUrl: String?)

@Serializable
data class SearchUsersApiResponse(val users: List<UserProfileApiResponse>)

@Serializable
data class ChatApiResponse(val id: String, val name: String, val isGroup: Boolean, val members: List<UserProfileApiResponse>)

@Serializable
data class GetMyChatsApiResponse(val chats: List<ChatApiResponse>)

@Serializable
data class ErrorApiResponse(val error: String)


// --- Мапперы из NATS-моделей (от сервисов) в API-модели (клиенту) ---

fun NatsUserProfile.toApi() = UserProfileApiResponse(id, nickname, avatarUrl)

fun NatsAuthResponse.toApi() = AuthApiResponse(token, profile.toApi())

fun NatsSearchResponse.toApi() = SearchUsersApiResponse(users.map { it.toApi() })

// Конвертирует NatsChat в ChatApiResponse, определяя имя для DM чатов
fun NatsChat.toApi(currentUserId: String): ChatApiResponse {
    val chatName = if (this.isGroup || (this.name != null && this.name.isNotEmpty())) {
        this.name ?: "Group Chat" // Имя группы или стандартное имя
    } else {
        // Для DM-чатов показываем имя собеседника
        this.members.find { it.id != currentUserId }?.nickname ?: "Chat" // Имя другого участника или стандартное
    }
    return ChatApiResponse(
        id = this.id,
        name = chatName,
        isGroup = this.isGroup,
        members = this.members.map { it.toApi() }
    )
}

// Конвертирует NatsGetMyChatsResponse в GetMyChatsApiResponse
fun NatsGetMyChatsResponse.toApi(currentUserId: String) = GetMyChatsApiResponse(
    chats = this.chats.map { it.toApi(currentUserId) } // Используем маппер NatsChat.toApi
)