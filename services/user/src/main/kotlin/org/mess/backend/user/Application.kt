package org.mess.backend.user

import io.nats.client.Nats
import kotlinx.serialization.json.Json
import org.mess.backend.user.db.configureDatabase
import org.mess.backend.user.db.ProfileService

// (Тут должны быть data class'ы для NATS JSON)
@kotlinx.serialization.Serializable
data class UserCreatedEvent(val userId: String, val username: String)

val json = Json { ignoreUnknownKeys = true }

fun main() {
    val db = configureDatabase()
    val profileService = ProfileService(db)

    val natsConnection = Nats.connect("nats://nats:4222")
    println("User Service connected to NATS")

    // --- Обработчик Pub/Sub для "user.created" ---
    val eventDispatcher = natsConnection.createDispatcher { msg ->
        val eventJson = String(msg.data, Charsets.UTF_8)
        val event = json.decodeFromString(UserCreatedEvent.serializer(), eventJson)

        println("Received user.created event for ${event.username}")
        // Вызываем бизнес-логику (создаем профиль)
        profileService.createProfile(event.userId, event.username)

        // (Ответ не требуется, это Pub/Sub)
    }
    eventDispatcher.subscribe("user.created")

    // --- Обработчик Request-Reply для "user.profile.get" ---
    // ... (подписывается на "user.profile.get", лезет в БД, отвечает) ...

    // --- Обработчик Request-Reply для "user.profile.update" ---
    // ... (подписывается на "user.profile.update", обновляет БД, отвечает) ...
}