// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/Config.kt
package org.mess.backend.gateway

import org.slf4j.LoggerFactory

// Получаем логгер для этого файла

// Конфигурация для JWT (нужна для ВАЛИДАЦИИ токена)
data class JwtConfig(
    val secret: String,     // Секрет для проверки подписи
    val issuer: String,     // Ожидаемый издатель токена
    val audience: String,   // Ожидаемая аудитория токена
    val realm: String       // Используется Ktor для WWW-Authenticate заголовка при 401
)

// Главный конфиг приложения
data class AppConfig(
    val natsUrl: String, // URL для подключения к NATS
    val jwt: JwtConfig     // Конфигурация JWT
)

object Config {
    val log = LoggerFactory.getLogger("GatewayConfig")
    // Загружаем конфигурацию из переменных окружения
    fun load(): AppConfig {
        log.info("Loading gateway configuration from environment variables...")
        // Получаем значения из окружения или используем значения по умолчанию
        val natsUrl = System.getenv("NATS_URL") ?: "nats://localhost:4222".also {
            log.warn("NATS_URL not set, using default: {}", it)
        }
        val jwtSecret = System.getenv("JWT_SECRET") ?: "default-super-secret-key-123".also {
            log.warn("JWT_SECRET not set, using default (INSECURE!)")
        }
        val jwtIssuer = System.getenv("JWT_ISSUER") ?: "org.mess.backend".also {
            log.warn("JWT_ISSUER not set, using default: {}", it)
        }
        // Эти значения должны СТРОГО совпадать с теми, что используются в auth-service
        val jwtAudience = "mess-users"
        val jwtRealm = "MessBackendGateway" // Для HTTP 401 ответа

        log.debug("NATS URL: {}", natsUrl)
        log.debug("JWT Issuer: {}", jwtIssuer)
        log.debug("JWT Audience: {}", jwtAudience)
        log.debug("JWT Realm: {}", jwtRealm)
        // Секрет JWT не логируем!

        return AppConfig(
            natsUrl = natsUrl,
            jwt = JwtConfig(
                secret = jwtSecret,
                issuer = jwtIssuer,
                audience = jwtAudience,
                realm = jwtRealm
            )
        )
    }
}