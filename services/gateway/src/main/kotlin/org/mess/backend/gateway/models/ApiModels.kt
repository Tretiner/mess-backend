// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/models/ApiModels.kt
package org.mess.backend.gateway.models.api

import kotlinx.serialization.Serializable
import org.mess.backend.gateway.models.nats.NatsChat // NATS модель чата
import org.mess.backend.gateway.models.nats.NatsGetMyChatsResponse // NATS ответ списка чатов
import org.mess.backend.gateway.models.nats.NatsSearchResponse // NATS ответ поиска
import org.mess.backend.gateway.models.nats.NatsUserProfile // NATS модель профиля

// --- Модели для ЗАПРОСОВ от клиента к REST API ---

@Serializable
data class AuthApiRequest(val username: String, val password: String)

@Serializable
data class UpdateProfileApiRequest(
    val newNickname: String? = null, // Поля опциональны для обновления
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)

@Serializable
data class CreateGroupChatApiRequest(val name: String, val memberIds: List<String>)

// --- Модель для СООБЩЕНИЯ от клиента в WebSocket ---
@Serializable
data class ChatMessageApiRequest(val chatId: String, val type: String, val content: String)


// --- Модели для ОТВЕТОВ клиенту от REST API ---

@Serializable
data class AuthApiResponse(val token: String, val profile: UserAuthProfileResponse) // Ответ на /auth/login, /auth/register

@Serializable
data class UserAuthProfileResponse( // Модель профиля для клиента
    val id: String,
    val nickname: String,
)

@Serializable
data class UserProfileApiResponse( // Модель профиля для клиента
    val id: String,
    val nickname: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

@Serializable
data class SearchUsersApiResponse(val users: List<UserProfileApiResponse>) // Ответ на /users/search

@Serializable
data class ChatApiResponse( // Модель чата для клиента
    val id: String,
    val name: String, // Имя чата или собеседника
    val isGroup: Boolean,
    val members: List<UserProfileApiResponse> // Список участников
)

@Serializable
data class GetMyChatsApiResponse(val chats: List<ChatApiResponse>) // Ответ на /chats

// --- Модель для СООБЩЕНИЯ клиенту через WebSocket ---
@Serializable
data class BroadcastMessageApiResponse( // Соответствует NatsBroadcastMessage
    val messageId: String,
    val chatId: String,
    val sender: UserProfileApiResponse, // Полный профиль отправителя
    val type: String,
    val content: String,
    val sentAt: String // kotlinx.datetime.Instant сериализуется в строку ISO 8601
)

// --- Модель для ОШИБОК от REST API ---
@Serializable
data class ErrorApiResponse(val error: String)


// --- Функции-мапперы: Конвертация из NATS моделей в API модели ---

// NatsUserProfile -> UserProfileApiResponse
fun NatsUserProfile.toApi() = UserProfileApiResponse(
    id = this.id,
    nickname = this.nickname,
    avatarUrl = this.avatarUrl,
    email = this.email,
    fullName = this.fullName
)

// NatsSearchResponse -> SearchUsersApiResponse
fun NatsSearchResponse.toApi() = SearchUsersApiResponse(
    users = this.users.map { it.toApi() } // Применяем маппер профиля к каждому элементу
)

// NatsChat -> ChatApiResponse
fun NatsChat.toApi(currentUserId: String): ChatApiResponse {
    // Определяем имя чата: имя группы или имя собеседника для DM
    val chatName = if (this.isGroup || (this.name != null && this.name.isNotEmpty())) {
        this.name ?: "Group Chat" // Имя группы или стандартное имя
    } else {
        // Ищем другого участника в DM чате
        this.members.find { it.id != currentUserId }?.nickname ?: "Chat" // Имя собеседника или стандартное
    }
    return ChatApiResponse(
        id = this.id,
        name = chatName,
        isGroup = this.isGroup,
        members = this.members.map { it.toApi() } // Конвертируем профили участников
    )
}

// NatsGetMyChatsResponse -> GetMyChatsApiResponse
fun NatsGetMyChatsResponse.toApi(currentUserId: String) = GetMyChatsApiResponse(
    chats = this.chats.map { it.toApi(currentUserId) } // Применяем маппер чата к каждому элементу
)