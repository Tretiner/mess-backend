package org.mess.backend.gateway.models

import org.mess.backend.gateway.models.api.AuthApiResponse
import org.mess.backend.gateway.models.api.ChatApiResponse
import org.mess.backend.gateway.models.api.GetMyChatsApiResponse
import org.mess.backend.gateway.models.api.SearchUsersApiResponse
import org.mess.backend.gateway.models.api.UserProfileApiResponse
import org.mess.backend.gateway.models.nats.NatsAuthResponse
import org.mess.backend.gateway.models.nats.NatsChat
import org.mess.backend.gateway.models.nats.NatsGetMyChatsResponse
import org.mess.backend.gateway.models.nats.NatsSearchResponse
import org.mess.backend.gateway.models.nats.NatsUserProfile

/**
 * Файл содержит функции-мапперы для преобразования данных
 * из NATS-моделей (внутренний формат микросервисов) в API-модели (внешний формат для клиента).
 */

// --- 1. Профили пользователей ---

/**
 * Преобразует полную NATS-модель профиля в API-модель.
 */
fun NatsUserProfile.toApi() = UserProfileApiResponse(
    id = this.id,
    username = this.username,
    avatarUrl = this.avatarUrl,
    email = this.email,
    fullName = this.fullName
)

/**
 * Преобразует NATS-ответ поиска в API-ответ поиска.
 */
fun NatsSearchResponse.toApi() = SearchUsersApiResponse(
    users = this.users.map { it.toApi() } // Применяем маппер профиля к каждому элементу
)


// --- 2. Аутентификация (Login/Register) ---

/**
 * Маппер для объединения успешного ответа аутентификации (токен) и полного профиля.
 * Используется в /auth/login и /auth/register.
 */
fun mapAuthAndProfileToApi(authResponse: NatsAuthResponse, profileResponse: NatsUserProfile): AuthApiResponse {
    return AuthApiResponse(
        accessToken = authResponse.accessToken,
        // profile: используем полный профиль, полученный от user-service
        profile = profileResponse.toApi()
    )
}

/**
 * Маппер для случая, когда user-service недоступен сразу после регистрации.
 * Возвращает токен, но профиль заполняет только базовыми данными из заглушки.
 */
fun mapAuthToApiWithStub(authResponse: NatsAuthResponse): AuthApiResponse {
    val stubProfileApi = UserProfileApiResponse(
        id = authResponse.profile.id,
        username = authResponse.profile.username, // Используем username из заглушки
        avatarUrl = null,
        email = null,
        fullName = null
    )
    return AuthApiResponse(
        accessToken = authResponse.accessToken,
        profile = stubProfileApi
    )
}


// --- 3. Чаты ---

/**
 * Преобразует NATS-модель чата в API-модель чата.
 * Определяет имя чата, используя логику DM/Group.
 * @param currentUserId ID пользователя, делающего запрос (нужен для DM).
 */
fun NatsChat.toApi(currentUserId: String): ChatApiResponse {
    // 1. Определяем имя чата:
    val chatName = if (this.isGroup || !this.name.isNullOrEmpty()) {
        this.name ?: "Group Chat"
    } else {
        // Находим другого участника в DM чате и используем его имя пользователя
        this.members.find { it.id != currentUserId }?.username ?: "Chat"
    }

    return ChatApiResponse(
        id = this.id,
        name = chatName,
        isGroup = this.isGroup,
        // Конвертируем профили участников
        members = this.members.map { it.toApi() }
    )
}

/**
 * Преобразует NATS-ответ со списком чатов в API-ответ со списком чатов.
 * @param currentUserId ID текущего пользователя.
 */
fun NatsGetMyChatsResponse.toApi(currentUserId: String) = GetMyChatsApiResponse(
    chats = this.chats.map { it.toApi(currentUserId) } // Применяем маппер чата к каждому элементу
)

// --- 4. Refresh Token (Удалены, так как мы используем долгоживущий Access Token) ---