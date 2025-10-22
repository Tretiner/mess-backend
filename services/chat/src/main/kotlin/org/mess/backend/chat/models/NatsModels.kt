// FILE: services/chat/src/main/kotlin/org/mess/backend/chat/models/NatsModels.kt
package org.mess.backend.chat.models

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.mess.backend.core.NatsErrorResponse // Assuming shared core module

// --- Models we LISTEN to (Events & Requests from Gateway) ---
@Serializable data class NatsIncomingMessage(val userId: String, val chatId: String, val type: String, val content: String)
@Serializable data class NatsChatCreateGroupRequest(val creatorId: String, val name: String, val memberIds: List<String>)
@Serializable data class NatsChatCreateDmRequest(val userId1: String, val userId2: String)
@Serializable data class NatsGetMyChatsRequest(val userId: String)
@Serializable data class NatsAddUserToChatRequest(val addedByUserId: String, val chatId: String, val userIdToAdd: String)

// --- Models we SEND (Events & Replies to Gateway) ---
@Serializable
data class NatsBroadcastMessage(
    val messageId: String,
    val chatId: String,
    val sender: NatsUserProfile, // Uses the updated NatsUserProfile
    val type: String,
    val content: String,
    val sentAt: Instant
)
@Serializable
data class NatsChat(
    val id: String,
    val name: String?,
    val isGroup: Boolean,
    val members: List<NatsUserProfile> // Uses the updated NatsUserProfile
)
@Serializable
data class NatsGetMyChatsResponse(val chats: List<NatsChat>)

// --- Models for communication WITH user-service ---
@Serializable data class NatsProfileGetRequest(val userId: String) // Request TO user-service

@Serializable
data class NatsUserProfile( // Expected response FROM user-service
    val id: String,
    val username: String, // <-- RENAMED from nickname
    val avatarUrl: String?,
    val email: String?,
    val fullName: String?
)

// NatsErrorResponse is likely in the core module
// @Serializable data class NatsErrorResponse(val error: String)