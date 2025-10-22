package org.mess.backend.chat.services

import io.nats.client.Connection
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.mess.backend.chat.models.NatsProfileGetRequest
import org.mess.backend.chat.models.NatsUserProfile
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.encodeToBytes
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/**
 * Сервис-клиент для общения с `user-service` по NATS.
 */
class RemoteProfileService(
    private val nats: Connection,
    private val json: Json = DefaultJson
) {
    // Фейковый профиль на случай, если user-service недоступен
    private val fallbackProfile = NatsUserProfile("unknown-id", "Unknown User", null, null, null)

    /**
     * Делает Request-Reply вызов в `user-service`, чтобы получить профиль.
     */
    suspend fun getProfile(userId: UUID): NatsUserProfile {
        return try {
            val request = NatsProfileGetRequest(userId.toString())
            val requestJson = json.encodeToBytes<NatsProfileGetRequest>(request)

            // Используем async-await (из kotlinx.coroutines.future)
            val future = nats.request(
                "user.profile.get",
                requestJson
            )
            val reply = future.await()

            val replyJson = String(reply.data, StandardCharsets.UTF_8)

            try {
                json.decodeFromString<NatsUserProfile>(replyJson)
            } catch (e: Exception) {
                // Пытаемся распарсить как ОШИБКУ
                val error = json.decodeFromString<NatsErrorResponse>(replyJson)
                println("Error from user-service: ${error.error}")
                fallbackProfile.copy(id = userId.toString())
            }

        } catch (e: Exception) {
            println("Failed to request user.profile.get for $userId: ${e.message}")
            fallbackProfile.copy(id = userId.toString())
        }
    }
}