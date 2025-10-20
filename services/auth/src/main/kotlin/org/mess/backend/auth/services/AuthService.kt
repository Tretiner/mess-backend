package org.mess.backend.auth.services

import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.auth.db.AuthUsersTable
import org.mess.backend.auth.models.NatsUserProfile
import org.mindrot.jbcrypt.BCrypt
import java.util.*

// Результат регистрации, который мы возвращаем в Application.kt
data class RegistrationResult(
    val token: String?,
    val profile: NatsUserProfile?,
    val userId: UUID?
)

// Результат логина
data class LoginResult(
    val token: String?,
    val profile: NatsUserProfile?
)

class AuthService(private val tokenService: TokenService) {

    fun registerUser(username: String, password: String): RegistrationResult {
        return transaction {
            // 1. Проверяем, не занят ли username
            val existing = AuthUsersTable.selectAll().where { AuthUsersTable.username eq username }.count()
            if (existing > 0) {
                return@transaction RegistrationResult(null, null, null) // Пользователь занят
            }

            // 2. Хэшируем пароль
            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            // 3. Создаем пользователя
            val newUserId = AuthUsersTable.insert {
                it[AuthUsersTable.username] = username
                it[AuthUsersTable.passwordHash] = passwordHash
            } get AuthUsersTable.id

            // 4. Генерируем токен
            val token = tokenService.createToken(newUserId.value, username)

            // 5. Создаем "профиль" (пока только из username)
            val profile = NatsUserProfile(username)

            RegistrationResult(token, profile, newUserId.value)
        }
    }

    fun loginUser(username: String, password: String): LoginResult {
        return transaction {
            val row = AuthUsersTable.selectAll().where { AuthUsersTable.username eq username }.firstOrNull()
                ?: return@transaction LoginResult(null, null) // Пользователь не найден

            val userId = row[AuthUsersTable.id].value
            val hash = row[AuthUsersTable.passwordHash]

            // Проверяем пароль
            if (BCrypt.checkpw(password, hash)) {
                // Пароль верный, генерируем токен
                val token = tokenService.createToken(userId, username)
                val profile = NatsUserProfile(username)
                LoginResult(token, profile)
            } else {
                LoginResult(null, null) // Неверный пароль
            }
        }
    }
}