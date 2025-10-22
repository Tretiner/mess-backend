package org.mess.backend.auth.models

import kotlinx.serialization.Serializable
import org.mess.backend.core.NatsErrorResponse // Используем core

// --- Existing Models ---
@Serializable
data class NatsAuthRequest(val username: String, val password: String)

// --- UPDATED Auth Response (Simplified) ---
@Serializable
data class NatsAuthResponse(
    val accessToken: String, // <-- Только Access Token
    val profile: NatsUserProfileStub
)

@Serializable
data class NatsUserProfileStub(
    val id: String,
    val username: String
)

@Serializable
data class UserCreatedEvent(
    val userId: String,
    val username: String
)

// Модели для Refresh токена УДАЛЕНЫ.