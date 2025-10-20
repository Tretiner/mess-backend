package org.mess.backend.auth

import org.mess.backend.auth.db.DatabaseConfig

// Конфиг для JWT
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val validityMs: Long
)

// Главный конфиг приложения
data class AppConfig(
    val natsUrl: String,
    val db: DatabaseConfig,
    val jwt: JwtConfig
)

object Config {
    // Загружаем конфиг из переменных окружения
    fun load(): AppConfig {
        return AppConfig(
            natsUrl = System.getenv("NATS_URL") ?: "nats://localhost:4222",
            db = DatabaseConfig(
                url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/auth_db",
                user = System.getenv("DB_USER") ?: "auth_user",
                password = System.getenv("DB_PASS") ?: "auth_pass"
            ),
            jwt = JwtConfig(
                secret = System.getenv("JWT_SECRET") ?: "default-super-secret-key-123",
                issuer = System.getenv("JWT_ISSUER") ?: "org.mess.backend",
                audience = "mess-users",
                realm = "MessBackend",
                validityMs = System.getenv("JWT_VALIDITY_MS")?.toLongOrNull() ?: 36_000_000 // 10 часов
            )
        )
    }
}