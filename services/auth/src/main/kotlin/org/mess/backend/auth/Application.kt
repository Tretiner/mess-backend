package org.mess.backend.auth

import org.mess.backend.core.*
import io.nats.client.Connection
import io.nats.client.Nats
import org.mess.backend.auth.db.initDatabase
import org.mess.backend.auth.models.*
import org.mess.backend.auth.services.AuthService
import org.mess.backend.auth.services.TokenService
import org.slf4j.LoggerFactory

fun main() {
//    val lifecycleScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // 1. Загружаем конфигурацию (из env variables)
    val config = Config.load(LoggerFactory.getLogger("ConfigLog"))
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
            val request = DefaultJson.decodeFromBytes<NatsAuthRequest>(msg.data)

            val (token, profile, userId) = authService.registerUser(request.username, request.password)

            if (token != null && profile != null && userId != null) {
                // A. Отправляем ответ (Reply)
                val response = NatsAuthResponse(token, profile)
                nats.publish(msg.replyTo, DefaultJson.encodeToBytes(response))

                // B. Публикуем событие (Publish)
                val event = UserCreatedEvent(userId.toString(), profile.username)
                nats.publish("user.created", DefaultJson.encodeToBytes(event))

            } else {
                // Отправляем ошибку
                val error = NatsErrorResponse("User already exists or registration failed.")
                nats.publish(msg.replyTo, DefaultJson.encodeToBytes(error))
            }
        } catch (e: Exception) {
            println("Error processing auth.register: ${e.message}")
            val error = NatsErrorResponse(e.message ?: "Unknown error")
            nats.publish(msg.replyTo, DefaultJson.encodeToBytes(error))
        }
    }
    regDispatcher.subscribe("auth.register")


    // 6. Настраиваем слушателя для ЛОГИНА (Request-Reply)
    val loginDispatcher = nats.createDispatcher { msg ->
        try {
            val request = DefaultJson.decodeFromBytes<NatsAuthRequest>(msg.data)

            val (token, profile) = authService.loginUser(request.username, request.password)

            if (token != null && profile != null) {
                // Отправляем успешный ответ
                val response = NatsAuthResponse(token, profile)
                nats.publish(msg.replyTo, DefaultJson.encodeToBytes(response))
            } else {
                // Отправляем ошибку
                val error = NatsErrorResponse("Invalid username or password.")
                nats.publish(msg.replyTo, DefaultJson.encodeToBytes(error))
            }
        } catch (e: Exception) {
            println("Error processing auth.login: ${e.message}")
            val error = NatsErrorResponse(e.message ?: "Unknown error")
            nats.publish(msg.replyTo, DefaultJson.encodeToBytes(error))
        }
    }
    loginDispatcher.subscribe("auth.login")

    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}