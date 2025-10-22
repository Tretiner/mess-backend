package org.mess.backend.gateway.models.nats

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.mess.backend.core.NatsErrorResponse

// --- Models for `auth-service` ---
@Serializable
data class NatsAuthRequest(
    val username: String,
    val password: String
)
// УДАЛЕНО: refreshToken
@Serializable
data class NatsAuthResponse(
    val accessToken: String,
    val profile: NatsUserProfileStub
)
@Serializable
data class NatsUserProfileStub(
    val id: String,
    val username: String
)
// NatsTokenRefreshRequest/Response УДАЛЕНЫ.

// --- Models for `user-service` ---
@Serializable
data class NatsProfileGetRequest(val userId: String)
@Serializable
data class NatsProfileUpdateRequest(val userId: String, val newUsername: String? = null, val newAvatarUrl: String? = null, val newEmail: String? = null, val newFullName: String? = null)
@Serializable
data class NatsSearchRequest(val query: String)
@Serializable
data class NatsUserProfile(val id: String, val username: String, val avatarUrl: String?, val email: String?, val fullName: String?)
@Serializable
data class NatsSearchResponse(val users: List<NatsUserProfile>)

// --- Models for `chat-service` ---
@Serializable data class NatsIncomingMessage(val userId: String, val chatId: String, val type: String, val content: String)
@Serializable data class NatsChatCreateGroupRequest(val creatorId: String, val name: String, val memberIds: List<String>)
@Serializable data class NatsChatCreateDmRequest(val userId1: String, val userId2: String)
@Serializable data class NatsGetMyChatsRequest(val userId: String)
@Serializable data class NatsAddUserToChatRequest(val addedByUserId: String, val chatId: String, val userIdToAdd: String)
@Serializable data class NatsBroadcastMessage(val messageId: String, val chatId: String, val sender: NatsUserProfile, val type: String, val content: String, val sentAt: Instant)
@Serializable data class NatsChat(val id: String, val name: String?, val isGroup: Boolean, val members: List<NatsUserProfile>)
@Serializable data class NatsGetMyChatsResponse(val chats: List<NatsChat>)