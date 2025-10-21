package org.mess.backend.user

import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.decodeFromBytes
import org.mess.backend.user.db.initDatabase
import org.mess.backend.user.models.*
import org.mess.backend.user.services.ProfileService
import java.nio.charset.StandardCharsets
import java.util.UUID

// Глобальный JSON-парсер
val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun main() {
    // 1. Загружаем конфигурацию
    val config = Config.load()
    println("Config loaded. Connecting to NATS at ${config.natsUrl}")

    // 2. Инициализируем сервисы
    val profileService = ProfileService()

    // 3. Подключаемся к БД
    initDatabase(config.db)
    println("Database connected.")

    // 4. Подключаемся к NATS
    val nats: Connection = Nats.connect(config.natsUrl)
    println("NATS connected. Awaiting requests...")

    // --- СЛУШАТЕЛЬ 1: Событие `user.created` (Pub/Sub) ---
    val eventDispatcher = nats.createDispatcher { msg ->
        try {
            val event = DefaultJson.decodeFromBytes<UserCreatedEvent>(msg.data)

            println("Received user.created event for ${event.username}")

            // Вызываем бизнес-логику (создаем профиль)
            profileService.createProfile(UUID.fromString(event.userId), event.username)
            // Ответ не требуется (это Pub/Sub)

        } catch (e: Exception) {
            println("Error processing user.created event: ${e.message}")
        }
    }
    eventDispatcher.subscribe("user.created")

    // --- СЛУШАТЕЛЬ 2: Запрос `user.profile.get` (Request-Reply) ---
    val getDispatcher = nats.createDispatcher { msg ->
        var responseJson: String
        try {
            val request = DefaultJson.decodeFromBytes<NatsProfileGetRequest>(msg.data)

            val profile = profileService.getProfile(UUID.fromString(request.userId))

            responseJson = if (profile != null) {
                json.encodeToString(profile)
            } else {
                json.encodeToString(NatsErrorResponse("Profile not found for user ${request.userId}"))
            }
        } catch (e: Exception) {
            responseJson = json.encodeToString(NatsErrorResponse(e.message ?: "Unknown error"))
        }
        nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
    }
    getDispatcher.subscribe("user.profile.get")

    // --- СЛУШАТЕЛЬ 3: Запрос `user.profile.update` (Request-Reply) ---
    val updateDispatcher = nats.createDispatcher { msg ->
        var responseJson: String
        try {
            val request = DefaultJson.decodeFromBytes<NatsProfileUpdateRequest>(msg.data)

            // Вызываем обновленный метод сервиса
            val updatedProfile = profileService.updateProfile(
                userId = UUID.fromString(request.userId),
                newUsername = request.newUsername,
                newAvatarUrl = request.newAvatarUrl,
                newEmail = request.newEmail, // <-- Новое поле
                newFullName = request.newFullName // <-- Новое поле
            )
            responseJson = json.encodeToString(updatedProfile) // Отправляем обновленный профиль

        } catch (e: Exception) {
            responseJson = json.encodeToString(NatsErrorResponse(e.message ?: "Unknown error"))
        }
        nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
    }
    updateDispatcher.subscribe("user.profile.update")

    // --- СЛУШАТЕЛЬ 4: Запрос `user.search` (Request-Reply) ---
    val searchDispatcher = nats.createDispatcher { msg ->
        var responseJson: String
        try {
            val requestJson = String(msg.data, StandardCharsets.UTF_8)
            val request = json.decodeFromString<NatsSearchRequest>(requestJson)

            val response = profileService.searchProfiles(request.query)
            responseJson = json.encodeToString(response)

        } catch (e: Exception) {
            responseJson = json.encodeToString(NatsErrorResponse(e.message ?: "Unknown error"))
        }
        nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
    }
    searchDispatcher.subscribe("user.search")

    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}