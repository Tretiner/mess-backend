// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/Config.kt
package org.mess.backend.gateway

import org.slf4j.LoggerFactory

// Конфиг для JWT (нужен только для валидации токена, получаемого от клиента)
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String // Используется Ktor для WWW-Authenticate заголовка при ошибке 401
)

// Главный конфиг приложения
data class AppConfig(
    val natsUrl: String,
    val jwt: JwtConfig
)

object Config {
    // Получаем логгер для этого файла
    private val log = LoggerFactory.getLogger("GatewayConfig")

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
        val jwtAudience = "mess-users" // Этот параметр обычно жестко задан
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