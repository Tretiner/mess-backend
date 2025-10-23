package org.mess.backend.gateway.models.api

import kotlinx.serialization.Serializable

// --- API Request Models (Client -> Gateway) ---
@Serializable
data class AuthApiRequest(val username: String, val password: String)

@Serializable
data class UpdateProfileApiRequest(
    val newUsername: String? = null,
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)

@Serializable
data class CreateGroupChatApiRequest( // UPDATED
    val name: String,
    val memberIds: List<String>,
    val avatarUrl: String? = null
)

@Serializable
data class UpdateChatApiRequest( // NEW
    val newName: String? = null,
    val newAvatarUrl: String? = null
)

@Serializable
data class ChatMessageApiRequest(val chatId: String, val type: String, val content: String)

// --- API Response Models (Gateway -> Client) ---
@Serializable
data class AuthApiResponse(val accessToken: String, val profile: UserProfileApiResponse)

@Serializable
data class UserProfileApiResponse(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

@Serializable
data class SearchUsersApiResponse(val users: List<UserProfileApiResponse>)

@Serializable
data class ApiLastMessage( // NEW
    val content: String,
    val senderName: String,
    val timestamp: String // ISO 8601 String
)

@Serializable
data class ChatApiResponse( // UPDATED
    val id: String,
    val name: String, // Prepared name
    val isGroup: Boolean,
    val members: List<UserProfileApiResponse>,
    val creatorId: String?, // Admin ID
    val chatAvatarUrl: String?, // Prepared avatar
    val lastMessage: ApiLastMessage?
)

@Serializable
data class GetMyChatsApiResponse(val chats: List<ChatApiResponse>)

@Serializable
data class BroadcastMessageApiResponse(
    val messageId: String,
    val chatId: String,
    val sender: UserProfileApiResponse,
    val type: String,
    val content: String,
    val sentAt: String // ISO 8601 String
)

@Serializable
data class GetChatMessagesApiResponse(
    val messages: List<BroadcastMessageApiResponse>
)

@Serializable
data class ErrorApiResponse(val error: String)