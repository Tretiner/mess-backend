// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/Application.kt
package org.mess.backend.gateway

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.websocket.* // Импорт плагина WebSockets
import io.nats.client.Connection
import io.nats.client.Nats
import org.mess.backend.core.DefaultJson
import org.mess.backend.gateway.plugins.* // Импортируем все наши функции конфигурации плагинов
import org.mess.backend.gateway.services.NatsClient
import org.slf4j.LoggerFactory
import java.time.Duration // Импорт для настроек WebSocket

val json = DefaultJson

// Глобальный логгер
val log = LoggerFactory.getLogger("GatewayApplication")

fun main() {
    // 1. Загружаем конфигурацию
    val config = Config.load()
    log.info("Config loaded. Connecting to NATS at ${config.natsUrl}")

    // 2. Подключаемся к NATS
    val natsConnection = try {
        Nats.connect(config.natsUrl)
    } catch (e: Exception) {
        log.error("CRITICAL: Failed to connect to NATS at {}. Shutting down. Error: {}", config.natsUrl, e.message, e)
        throw e // Завершаем работу, если нет NATS
    }
    log.info("NATS connected successfully.")

    // 3. Создаем NATS-клиент (хелпер)
    val natsClient = NatsClient(natsConnection, json)

    // 4. Запускаем Ktor-сервер
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    log.info("Starting Ktor server on host {}, port {}", host, port)

    embeddedServer(
        Netty,
        port = port,
        host = host,
        module = { module(config, natsClient, natsConnection) }
    ).start(wait = true) // wait = true блокирует основной поток, пока сервер работает
}

// Модуль Ktor-приложения для организации кода
fun Application.module(config: AppConfig, natsClient: NatsClient, natsConnection: Connection) {
    // Устанавливаем базовые плагины Ktor
    configureSerialization()    // Настройка JSON
    configureSecurity(config.jwt) // Настройка JWT-аутентификации
    configureStatusPages()      // Обработка ошибок HTTP
    configureLogging()          // Логирование HTTP-запросов

    // Устанавливаем плагин WebSockets с настройками
    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15) // Отправлять пинг каждые 15 сек
        timeout = Duration.ofSeconds(15)    // Таймаут ожидания понга
        maxFrameSize = Long.MAX_VALUE       // Максимальный размер фрейма
        masking = false                     // Отключаем маскирование (обычно нужно только для браузеров)
    }

    // Передаем зависимости и настраиваем маршруты (REST и WebSocket)
    configureRouting(natsClient, natsConnection)

    log.info("Ktor server configured and running.")

    // Обработка завершения работы (graceful shutdown)
    environment.monitor.subscribe(ApplicationStopping) {
        log.info("Ktor server stopping...")
        natsConnection.close() // Закрываем соединение с NATS
        log.info("NATS connection closed.")
    }
}