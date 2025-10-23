package org.mess.backend.gateway.models.nats

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// --- Models for `auth-service` ---
@Serializable
data class NatsAuthRequest(
    val username: String,
    val password: String
)

@Serializable
data class NatsAuthResponse(
    val accessToken: String,
    val profile: NatsUserProfileStub // A minimal profile
)

@Serializable
data class NatsUserProfileStub(
    val id: String,
    val username: String
)

// --- Models for `user-service` ---
@Serializable
data class NatsProfileGetRequest(val userId: String)

@Serializable
data class NatsProfilesGetBatchRequest(val userIds: List<String>) // NEW

@Serializable
data class NatsProfileUpdateRequest(
    val userId: String,
    val newUsername: String? = null,
    val newAvatarUrl: String? = null,
    val newEmail: String? = null,
    val newFullName: String? = null
)

@Serializable
data class NatsSearchRequest(val query: String)

@Serializable
data class NatsUserProfile(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

@Serializable
data class NatsProfilesGetBatchResponse(val profiles: List<NatsUserProfile>) // NEW

@Serializable
data class NatsSearchResponse(val users: List<NatsUserProfile>)

// --- Models for `chat-service` ---
@Serializable
data class NatsIncomingMessage(
    val userId: String,
    val chatId: String,
    val type: String,
    val content: String
)

@Serializable
data class NatsChatCreateGroupRequest(
    val creatorId: String,
    val name: String,
    val memberIds: List<String>,
    val avatarUrl: String? // UPDATED
)

@Serializable
data class NatsChatCreateDmRequest(val userId1: String, val userId2: String)

@Serializable
data class NatsGetMyChatsRequest(val userId: String)

@Serializable
data class NatsGetChatDetailsRequest(val chatId: String, val userId: String) // NEW

@Serializable
data class NatsUpdateChatRequest( // NEW
    val chatId: String,
    val requestedByUserId: String,
    val newName: String?,
    val newAvatarUrl: String?
)

@Serializable
data class NatsRemoveUserRequest( // NEW
    val chatId: String,
    val requestedByUserId: String,
    val userIdToRemove: String
)

@Serializable
data class NatsBroadcastMessage(
    val messageId: String,
    val chatId: String,
    val sender: NatsUserProfile,
    val type: String,
    val content: String,
    val sentAt: Instant // Note: Instant
)

@Serializable
data class NatsLastMessage( // NEW
    val content: String,
    val senderName: String,
    val timestamp: Instant
)

@Serializable
data class NatsMessagesGetRequest(
    val chatId: String,
    val userId: String,
    val limit: Int = 50,
    val beforeInstant: Instant? = null
)

@Serializable
data class NatsMessagesGetResponse(
    val messages: List<NatsBroadcastMessage> // Reuse NatsBroadcastMessage
)

@Serializable
data class NatsChat( // UPDATED
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<NatsUserProfile>,
    val creatorId: String?,
    val chatAvatarUrl: String?,
    val lastMessage: NatsLastMessage?
)

@Serializable
data class NatsGetMyChatsResponse(val chats: List<NatsChat>)