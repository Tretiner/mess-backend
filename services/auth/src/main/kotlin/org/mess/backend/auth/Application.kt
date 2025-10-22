package org.mess.backend.auth

import io.nats.client.Connection
import io.nats.client.Nats
import org.mess.backend.auth.db.DatabaseConfig
import org.mess.backend.auth.db.initDatabase
import org.mess.backend.auth.models.* // Local NATS models for this service
import org.mess.backend.auth.services.AuthService
import org.mess.backend.auth.services.TokenService
import org.mess.backend.core.DefaultJson // Import shared Json config
import org.mess.backend.core.NatsErrorResponse // Import shared Error model
import org.mess.backend.core.decodeFromBytes // Import extension function
import org.mess.backend.core.encodeToBytes // Import extension function
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

// Use the shared Json instance
val json = DefaultJson
val log = LoggerFactory.getLogger("AuthApplication")

fun main() {
    val config = Config.load(log)
    log.info("Config loaded. Connecting to NATS at ${config.natsUrl}")

    val tokenService = TokenService(config.jwt)
    val authService = AuthService(tokenService)

    try {
        initDatabase(config.db)
        log.info("Database connected successfully.")
    } catch (e: Exception) { /* ... error handling ... */ throw e }

    val nats: Connection = try {
        Nats.connect(config.natsUrl)
    } catch (e: Exception) { /* ... error handling ... */ throw e }
    log.info("NATS connected. Awaiting requests...")

    // --- Register Handler ---
    val regDispatcher = nats.createDispatcher { msg ->
        try {
            val request = json.decodeFromBytes<NatsAuthRequest>(msg.data)
            log.debug("Received auth.register request for user: {}", request.username)

            // Вызываем registerUser, который теперь возвращает только accessToken
            val (accessToken, profileStub, userId) = authService.registerUser(request.username, request.password)

            if (accessToken != null && profileStub != null && userId != null) {
                // Отправляем ответ с ТОЛЬКО Access Token
                val response = NatsAuthResponse(accessToken, profileStub)
                nats.publish(msg.replyTo, json.encodeToBytes(response))
                log.info("Registration successful for user: {}", request.username)

                // Публикуем user.created event
                val event = UserCreatedEvent(userId.toString(), request.username)
                nats.publish("user.created", json.encodeToBytes(event))
                log.info("Published user.created event for user ID: {}", userId)
            } else {
                log.warn("Registration failed for user: {}", request.username)
                val error = NatsErrorResponse("User already exists or registration failed.")
                nats.publish(msg.replyTo, json.encodeToBytes(error))
            }
        } catch (e: Exception) {
            log.error("Error processing auth.register: ${e.message}", e)
            try {
                // Use encodeToBytes for error response
                val error = NatsErrorResponse(e.message ?: "Unknown error during registration")
                nats.publish(msg.replyTo, json.encodeToBytes(error))
            } catch (replyError: Exception) { /* ... */ }
        }
    }
    regDispatcher.subscribe("auth.register")

    // --- Login Handler ---
    val loginDispatcher = nats.createDispatcher { msg ->
        try {
            val request = json.decodeFromBytes<NatsAuthRequest>(msg.data)
            log.debug("Received auth.login request for user: {}", request.username)

            // Вызываем loginUser, который теперь возвращает только accessToken
            val (accessToken, profileStub) = authService.loginUser(request.username, request.password)

            if (accessToken != null && profileStub != null) {
                // Отправляем ответ с ТОЛЬКО Access Token
                val response = NatsAuthResponse(accessToken, profileStub)
                nats.publish(msg.replyTo, json.encodeToBytes(response))
                log.info("Login successful for user: {}", request.username)
            } else {
                log.warn("Login failed for user: {}", request.username)
                val error = NatsErrorResponse("Invalid username or password.")
                nats.publish(msg.replyTo, json.encodeToBytes(error))
            }
        } catch (e: Exception) {
            log.error("Error processing auth.login: ${e.message}", e)
            try {
                val error = NatsErrorResponse(e.message ?: "Unknown error during login")
                // Use encodeToBytes
                nats.publish(msg.replyTo, json.encodeToBytes(error))
            } catch (replyError: Exception) { /* ... */ }
        }
    }
    loginDispatcher.subscribe("auth.login")

    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}