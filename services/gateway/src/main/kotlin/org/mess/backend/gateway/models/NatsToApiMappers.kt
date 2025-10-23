package org.mess.backend.gateway.models

import org.mess.backend.gateway.models.api.*
import org.mess.backend.gateway.models.nats.*
import kotlinx.datetime.Instant

// --- 1. Профили пользователей ---
fun NatsUserProfile.toApi() = UserProfileApiResponse(
    id = this.id,
    username = this.username,
    avatarUrl = this.avatarUrl,
    email = this.email,
    fullName = this.fullName
)

fun NatsSearchResponse.toApi() = SearchUsersApiResponse(
    users = this.users.map { it.toApi() }
)

// --- 2. Аутентификация ---
fun mapAuthAndProfileToApi(authResponse: NatsAuthResponse, profileResponse: NatsUserProfile): AuthApiResponse {
    return AuthApiResponse(
        accessToken = authResponse.accessToken,
        profile = profileResponse.toApi()
    )
}

fun mapAuthToApiWithStub(authResponse: NatsAuthResponse): AuthApiResponse {
    val stubProfileApi = UserProfileApiResponse(
        id = authResponse.profile.id,
        username = authResponse.profile.username,
        avatarUrl = null,
        email = null,
        fullName = null
    )
    return AuthApiResponse(
        accessToken = authResponse.accessToken,
        profile = stubProfileApi
    )
}

// --- 3. Сообщения ---
fun NatsLastMessage.toApi() = ApiLastMessage(
    content = this.content,
    senderName = this.senderName,
    timestamp = this.timestamp.toString() // Instant -> String
)

fun NatsBroadcastMessage.toApi() = BroadcastMessageApiResponse(
    messageId = this.messageId,
    chatId = this.chatId,
    sender = this.sender.toApi(),
    type = this.type,
    content = this.content,
    sentAt = this.sentAt.toString() // Instant -> String
)

// --- 4. Чаты ---
/**
 * Преобразует NATS-модель чата в API-модель чата.
 * Определяет имя и аватар чата, используя логику DM/Group.
 */
fun NatsChat.toApi(currentUserId: String): ChatApiResponse {
    var finalName = this.name ?: "Group Chat"
    var finalAvatarUrl = this.chatAvatarUrl

    if (!this.isGroup) {
        // Это DM. Находим другого участника.
        val otherMember = this.members.find { it.id != currentUserId }
        finalName = otherMember?.username ?: "Chat"
        finalAvatarUrl = otherMember?.avatarUrl // Используем аватар собеседника
    }

    return ChatApiResponse(
        id = this.id,
        name = finalName,
        isGroup = this.isGroup,
        members = this.members.map { it.toApi() },
        creatorId = this.creatorId,
        chatAvatarUrl = finalAvatarUrl,
        lastMessage = this.lastMessage?.toApi()
    )
}

fun NatsGetMyChatsResponse.toApi(currentUserId: String) = GetMyChatsApiResponse(
    chats = this.chats.map { it.toApi(currentUserId) }
)

fun NatsMessagesGetResponse.toApi(): GetChatMessagesApiResponse {
    return GetChatMessagesApiResponse(
        messages = this.messages.map { it.toApi() }
    )
}