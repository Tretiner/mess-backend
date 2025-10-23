package org.mess.backend.chat.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.mess.backend.core.NatsErrorResponse

// --- Models we LISTEN to (Events & Requests from Gateway) ---
@Serializable
data class NatsIncomingMessage(val userId: String, val chatId: String, val type: String, val content: String)

@Serializable
data class NatsChatCreateGroupRequest(
    val creatorId: String,
    val name: String,
    val memberIds: List<String>,
    val avatarUrl: String? // Необязательный аватар группы
)

@Serializable
data class NatsChatCreateDmRequest(val userId1: String, val userId2: String)

@Serializable
data class NatsGetMyChatsRequest(val userId: String)

@Serializable
data class NatsGetChatDetailsRequest(val chatId: String, val userId: String) // userId для getMyChats

@Serializable
data class NatsUpdateChatRequest(
    val chatId: String,
    val requestedByUserId: String,
    val newName: String?,
    val newAvatarUrl: String?
)

@Serializable
data class NatsRemoveUserRequest(
    val chatId: String,
    val requestedByUserId: String,
    val userIdToRemove: String
)

// --- Models we SEND (Events & Replies to Gateway) ---
@Serializable
data class NatsLastMessage(
    val content: String,
    val senderName: String,
    val timestamp: Instant
)

@Serializable
data class NatsChat(
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<NatsUserProfile>,
    val creatorId: String?, // ID админа
    val chatAvatarUrl: String?, // Аватар группы
    val lastMessage: NatsLastMessage? // Последнее сообщение
)

@Serializable
data class NatsGetMyChatsResponse(val chats: List<NatsChat>)

@Serializable
data class NatsBroadcastMessage(
    val messageId: String,
    val chatId: String,
    val sender: NatsUserProfile,
    val type: String,
    val content: String,
    val sentAt: Instant
)

// --- Models for communication WITH user-service ---
@Serializable
data class NatsProfileGetRequest(val userId: String) // Запрос к user-service

@Serializable
data class NatsProfilesGetBatchRequest(val userIds: List<String>) // Пакетный запрос

@Serializable
data class NatsUserProfile(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

@Serializable
data class NatsProfilesGetBatchResponse(val profiles: List<NatsUserProfile>)

@Serializable
data class NatsMessagesGetRequest(
    val chatId: String,
    val userId: String, // User requesting history (to check membership)
    val limit: Int = 50, // Default limit
    val beforeInstant: Instant? = null // For pagination (load older messages)
)

@Serializable
data class NatsMessagesGetResponse(
    val messages: List<NatsBroadcastMessage> // Reuse the broadcast model
)