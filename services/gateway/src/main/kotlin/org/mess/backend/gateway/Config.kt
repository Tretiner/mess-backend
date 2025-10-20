package org.mess.backend.gateway

// Конфиг для JWT (нужен только для валидации)
data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String // Используется Ktor для WWW-Authenticate заголовка
)

// Главный конфиг
data class AppConfig(
    val natsUrl: String,
    val jwt: JwtConfig
)

object Config {
    fun load(): AppConfig {
        // Загружаем из переменных окружения
        return AppConfig(
            natsUrl = System.getenv("NATS_URL") ?: "nats://localhost:4222",
            jwt = JwtConfig(
                secret = System.getenv("JWT_SECRET") ?: "default-super-secret-key-123",
                issuer = System.getenv("JWT_ISSUER") ?: "org.mess.backend",
                audience = "mess-users", // Должен совпадать с тем, что в auth-service
                realm = "MessBackendGateway"
            )
        )
    }
}