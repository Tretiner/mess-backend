// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/models/ApiModels.kt
package org.mess.backend.gateway.models.api

import kotlinx.serialization.Serializable

// --- API Request Models (Client -> Gateway) ---
@Serializable data class AuthApiRequest(val username: String, val password: String)
@Serializable data class UpdateProfileApiRequest(
    val newUsername: String? = null, // <-- RENAMED from newNickname
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)
@Serializable data class CreateGroupChatApiRequest(val name: String, val memberIds: List<String>)
@Serializable data class ChatMessageApiRequest(val chatId: String, val type: String, val content: String)

// --- API Response Models (Gateway -> Client) ---
@Serializable data class AuthApiResponse(val accessToken: String, val profile: UserProfileApiResponse)

@Serializable
data class UserProfileApiResponse( // The canonical API profile model
    val id: String,
    val username: String, // <-- RENAMED from nickname
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)
@Serializable data class SearchUsersApiResponse(val users: List<UserProfileApiResponse>)
@Serializable data class ChatApiResponse(val id: String, val name: String, val isGroup: Boolean, val members: List<UserProfileApiResponse>) // Contains full UserProfileApiResponse list
@Serializable data class GetMyChatsApiResponse(val chats: List<ChatApiResponse>)
@Serializable data class BroadcastMessageApiResponse( // WS message to client
    val messageId: String,
    val chatId: String,
    val sender: UserProfileApiResponse, // Contains full UserProfileApiResponse
    val type: String,
    val content: String,
    val sentAt: String
)
@Serializable data class ErrorApiResponse(val error: String)