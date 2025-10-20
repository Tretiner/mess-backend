package org.mess.backend.models

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant // ИЗМЕНЕНИЕ

// -------------------
// Модели для REST API
// -------------------

@Serializable
data class ChatCreateGroupRequest(
    val name: String,
    val memberIds: List<String> // Список ID пользователей для добавления
)

@Serializable
data class ChatMember(
    val id: String,
    val nickname: String
)

@Serializable
data class ChatResponse(
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<ChatMember>
)

// -------------------
// Модели для WebSocket
// -------------------

@Serializable
data class ChatMessage(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val senderNickname: String,
    val type: String, // "text", "image", "video", "voice"
    val content: String, // Текст или URL
    val sentAt: Instant // ИЗМЕНЕНИЕ
)

@Serializable
data class IncomingWsMessage(
    val chatId: String,
    val type: String, // "text" или "file_url"
    val content: String
)