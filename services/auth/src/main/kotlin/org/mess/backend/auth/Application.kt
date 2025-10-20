package org.mess.backend.auth

import io.ktor.server.config.* // Используем Ktor config HOCON/YAML
import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mess.backend.auth.db.DatabaseConfig
import org.mess.backend.auth.db.initDatabase
import org.mess.backend.auth.models.*
import org.mess.backend.auth.services.AuthService
import org.mess.backend.auth.services.TokenService
import java.nio.charset.StandardCharsets

// Глобальный JSON-парсер
val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

fun main() {
    // 1. Загружаем конфигурацию (из env variables)
    val config = Config.load()
    println("Config loaded. Connecting to NATS at ${config.natsUrl}")

    // 2. Инициализируем сервисы
    val tokenService = TokenService(config.jwt)
    val authService = AuthService(tokenService)

    // 3. Подключаемся к БД
    initDatabase(config.db)
    println("Database connected.")

    // 4. Подключаемся к NATS
    val nats: Connection = Nats.connect(config.natsUrl)
    println("NATS connected. Awaiting requests...")

    // 5. Настраиваем слушателя для РЕГИСТРАЦИИ (Request-Reply + Pub/Sub)
    val regDispatcher = nats.createDispatcher { msg ->
        try {
            val requestJson = String(msg.data, StandardCharsets.UTF_8)
            val request = json.decodeFromString<NatsAuthRequest>(requestJson)

            // Вызываем бизнес-логику
            val (token, profile, userId) = authService.registerUser(request.username, request.password)

            if (token != null && profile != null && userId != null) {
                // A. Отправляем ответ (Reply)
                val response = NatsAuthResponse(token, profile)
                val responseJson = json.encodeToString(response)
                nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))

                // B. Публикуем событие (Publish)
                val event = UserCreatedEvent(userId.toString(), profile.username)
                val eventJson = json.encodeToString(event)
                nats.publish("user.created", eventJson.toByteArray(StandardCharsets.UTF_8))

            } else {
                // Отправляем ошибку
                val error = NatsErrorResponse("User already exists or registration failed.")
                nats.publish(msg.replyTo, json.encodeToString(error).toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            println("Error processing auth.register: ${e.message}")
            val error = NatsErrorResponse(e.message ?: "Unknown error")
            nats.publish(msg.replyTo, json.encodeToString(error).toByteArray(StandardCharsets.UTF_8))
        }
    }
    regDispatcher.subscribe("auth.register")


    // 6. Настраиваем слушателя для ЛОГИНА (Request-Reply)
    val loginDispatcher = nats.createDispatcher { msg ->
        try {
            val requestJson = String(msg.data, StandardCharsets.UTF_8)
            val request = json.decodeFromString<NatsAuthRequest>(requestJson)

            // Вызываем бизнес-логику
            val (token, profile) = authService.loginUser(request.username, request.password)

            if (token != null && profile != null) {
                // Отправляем успешный ответ
                val response = NatsAuthResponse(token, profile)
                val responseJson = json.encodeToString(response)
                nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
            } else {
                // Отправляем ошибку
                val error = NatsErrorResponse("Invalid username or password.")
                nats.publish(msg.replyTo, json.encodeToString(error).toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            println("Error processing auth.login: ${e.message}")
            val error = NatsErrorResponse(e.message ?: "Unknown error")
            nats.publish(msg.replyTo, json.encodeToString(error).toByteArray(StandardCharsets.UTF_8))
        }
    }
    loginDispatcher.subscribe("auth.login")

    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}