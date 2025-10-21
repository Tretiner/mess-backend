// FILE: services/auth/src/main/kotlin/org/mess/backend/auth/Config.kt
package org.mess.backend.auth

import org.mess.backend.auth.db.DatabaseConfig
import org.slf4j.Logger

// Конфиг для генерации JWT
internal data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val validityMs: Long
)

// Главный конфиг приложения
internal data class AppConfig(
    val natsUrl: String,
    val db: DatabaseConfig,
    val jwt: JwtConfig
)

internal object Config {
    // Загружаем конфиг из переменных окружения
    fun load(log: Logger): AppConfig {
        log.info("Loading auth-service configuration from environment variables...")
        val natsUrl = System.getenv("NATS_URL") ?: "nats://localhost:4222"
        val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/auth_db"
        val dbUser = System.getenv("DB_USER") ?: "auth_user"
        val dbPass = System.getenv("DB_PASS") ?: "auth_pass"
        val jwtSecret = System.getenv("JWT_SECRET") ?: "default-super-secret-key-123"
        val jwtIssuer = System.getenv("JWT_ISSUER") ?: "org.mess.backend"
        val jwtAudience = "mess-users" // Должен совпадать с gateway
        val jwtValidity = System.getenv("JWT_VALIDITY_MS")?.toLongOrNull() ?: 36_000_000L // 10 часов

        log.debug("NATS URL: {}", natsUrl)
        log.debug("DB URL: {}", dbUrl)
        log.debug("DB User: {}", dbUser)
        log.debug("JWT Issuer: {}", jwtIssuer)
        log.debug("JWT Audience: {}", jwtAudience)
        log.debug("JWT Validity (ms): {}", jwtValidity)
        // Секрет JWT и пароль БД не логируем!

        return AppConfig(
            natsUrl = natsUrl,
            db = DatabaseConfig(
                url = dbUrl,
                user = dbUser,
                password = dbPass
            ),
            jwt = JwtConfig(
                secret = jwtSecret,
                issuer = jwtIssuer,
                audience = jwtAudience,
                validityMs = jwtValidity
            )
        )
    }
}