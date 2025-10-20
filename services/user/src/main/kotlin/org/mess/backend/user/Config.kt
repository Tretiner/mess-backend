package org.mess.backend.user

import org.mess.backend.user.db.DatabaseConfig

// Главный конфиг приложения
data class AppConfig(
    val natsUrl: String,
    val db: DatabaseConfig
)

object Config {
    // Загружаем конфиг из переменных окружения
    fun load(): AppConfig {
        return AppConfig(
            natsUrl = System.getenv("NATS_URL") ?: "nats://localhost:4222",
            db = DatabaseConfig(
                // Этот сервис должен иметь свою БД (или схему)
                url = System.getenv("DB_URL") ?: "jdbc:postgresql://localhost:5432/user_db",
                user = System.getenv("DB_USER") ?: "user_user",
                password = System.getenv("DB_PASS") ?: "user_pass"
            )
        )
    }
}