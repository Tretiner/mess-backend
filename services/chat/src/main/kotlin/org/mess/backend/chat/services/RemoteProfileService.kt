package org.mess.backend.chat.services

import io.nats.client.Connection
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import org.mess.backend.chat.models.*
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.decodeFromBytes
import org.mess.backend.core.encodeToBytes
import java.nio.charset.StandardCharsets
import java.util.UUID

class RemoteProfileService(
    private val nats: Connection,
    private val json: Json = DefaultJson
) {
    private val fallbackProfile = NatsUserProfile("unknown-id", "Unknown User", null, null, null)

    /**
     * Получает ОДИН профиль. Используется для отправителей сообщений, если их нет в кэше.
     */
    suspend fun getProfile(userId: UUID): NatsUserProfile {
        return try {
            val request = NatsProfileGetRequest(userId.toString())
            val reply = nats.request("user.profile.get", json.encodeToBytes(request)).await()
            val replyJson = String(reply.data, StandardCharsets.UTF_8)
            try {
                json.decodeFromString<NatsUserProfile>(replyJson)
            } catch (e: Exception) {
                val error = json.decodeFromString<NatsErrorResponse>(replyJson)
                println("Error from user-service: ${error.error}")
                fallbackProfile.copy(id = userId.toString())
            }
        } catch (e: Exception) {
            println("Failed to request user.profile.get for $userId: ${e.message}")
            fallbackProfile.copy(id = userId.toString())
        }
    }

    /**
     * Получает СПИСОК профилей за один NATS-запрос.
     * Возвращает Map для быстрого поиска.
     */
    suspend fun getProfilesBatch(userIds: Set<UUID>): Map<UUID, NatsUserProfile> {
        if (userIds.isEmpty()) return emptyMap()

        return try {
            val request = NatsProfilesGetBatchRequest(userIds.map { it.toString() })
            val reply = nats.request("user.profiles.get.batch", json.encodeToBytes(request)).await()

            val response = json.decodeFromBytes<NatsProfilesGetBatchResponse>(reply.data)
            response.profiles.associateBy { UUID.fromString(it.id) }
        } catch (e: Exception) {
            println("Failed to request user.profiles.get.batch: ${e.message}")
            // Возвращаем пустую карту или фейковые профили
            userIds.associateWith { fallbackProfile.copy(id = it.toString()) }
        }
    }
}