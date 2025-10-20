package org.mess.backend.chat.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// ---
// МОДЕЛИ, КОТОРЫЕ МЫ "СЛУШАЕМ" (События и Запросы)
// ---

// (Событие от Gateway) `chat.message.incoming`
@Serializable
data class NatsIncomingMessage(
    val userId: String, // ID отправителя из JWT
    val chatId: String,
    val type: String,
    val content: String
)

// (Запрос от Gateway) `chat.create.group`
@Serializable
data class NatsChatCreateGroupRequest(
    val creatorId: String,
    val name: String,
    val memberIds: List<String>
)

// (Запрос от Gateway) `chat.create.dm`
@Serializable
data class NatsChatCreateDmRequest(
    val userId1: String,
    val userId2: String
)

// (Запрос от Gateway) `chat.get.mychats`
@Serializable
data class NatsGetMyChatsRequest(
    val userId: String
)


// ---
// МОДЕЛИ, КОТОРЫЕ МЫ "ОТПРАВЛЯЕМ" (События и Ответы)
// ---

// (Событие для Gateway) `chat.message.broadcast`
@Serializable
data class NatsBroadcastMessage(
    val messageId: String,
    val chatId: String,
    val sender: NatsUserProfile, // ВАЖНО: полная инфо об отправителе
    val type: String,
    val content: String,
    val sentAt: Instant
)

// (Ответ для Gateway) Каноничная модель Чата
@Serializable
data class NatsChat(
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<NatsUserProfile> // ВАЖНО: полная инфо об участниках
)

// (Ответ для Gateway) `chat.get.mychats`
@Serializable
data class NatsGetMyChatsResponse(
    val chats: List<NatsChat>
)


// ---
// МОДЕЛИ ДЛЯ ОБЩЕНИЯ С `user-service`
// ---

// (Запрос к user-service) `user.profile.get`
@Serializable
data class NatsProfileGetRequest(
    val userId: String
)

// (Ответ от user-service)
// Мы *обязаны* иметь этот data class у себя, чтобы парсить ответ
@Serializable
data class NatsUserProfile(
    val id: String,
    val nickname: String,
    val avatarUrl: String?
)

// ---
// ОБЩИЕ МОДЕЛИ
// ---

@Serializable
data class NatsErrorResponse(
    val error: String
)