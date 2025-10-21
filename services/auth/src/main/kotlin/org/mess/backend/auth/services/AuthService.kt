// FILE: services/auth/src/main/kotlin/org/mess/backend/auth/services/AuthService.kt
package org.mess.backend.auth.services

import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.auth.db.AuthUsersTable
import org.mess.backend.auth.models.NatsUserProfileStub
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

// Результат регистрации, который мы возвращаем в Application.kt
internal data class RegistrationResult(
    val token: String?,
    val profile: NatsUserProfileStub?, // Возвращаем заглушку
    val userId: UUID?
)

// Результат логина
internal data class LoginResult(
    val token: String?,
    val profile: NatsUserProfileStub? // Возвращаем заглушку
)

val log: Logger = LoggerFactory.getLogger("AuthService")

/**
 * Сервис, отвечающий за бизнес-логику регистрации и входа.
 */
internal class AuthService(private val tokenService: TokenService) {

    /**
     * Регистрирует нового пользователя.
     * @param username Имя пользователя (логин).
     * @param password Пароль пользователя.
     * @return RegistrationResult с токеном, заглушкой профиля и ID, или null-значения при ошибке.
     */
    fun registerUser(username: String, password: String): RegistrationResult {
        return try {
            transaction { // Все операции с БД в одной транзакции
                // 1. Проверяем, не занят ли username (регистронезависимо)
                val existing = AuthUsersTable
                    .selectAll().where { AuthUsersTable.username.lowerCase() eq username.lowercase() }
                    .count()
                if (existing > 0) {
                    return@transaction RegistrationResult(null, null, null) // Пользователь занят
                }

                // 2. Хэшируем пароль
                val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

                // 3. Создаем пользователя в БД
                // Используем insertAndGetId для получения ID после вставки
                val newUserId = AuthUsersTable.insertAndGetId {
                    it[AuthUsersTable.username] = username
                    it[AuthUsersTable.passwordHash] = passwordHash
                }

                // 4. Генерируем токен
                val token = tokenService.createToken(newUserId.value, username)

                // 5. Создаем "заглушку" профиля для ответа
                val profileStub = NatsUserProfileStub(id = newUserId.value.toString(), username = username)

                RegistrationResult(token, profileStub, newUserId.value)
            }
        } catch (e: ExposedSQLException) {
            // Обработка специфических ошибок БД, если нужно (например, нарушение unique constraint)
            log.error("Database error during registration for user '{}': {}", username, e.message)
            RegistrationResult(null, null, null)
        } catch (e: Exception) {
            log.error("Unexpected error during registration for user '{}': {}", username, e.message, e)
            RegistrationResult(null, null, null)
        }
    }

    /**
     * Выполняет вход пользователя.
     * @param username Имя пользователя (логин).
     * @param password Пароль пользователя.
     * @return LoginResult с токеном и заглушкой профиля, или null-значения при ошибке.
     */
    fun loginUser(username: String, password: String): LoginResult {
        return try {
            transaction {
                // Ищем пользователя по имени (регистронезависимо)
                val row = AuthUsersTable
                    .selectAll()
                    .where { AuthUsersTable.username.lowerCase() eq username.lowercase() }
                    .firstOrNull()
                    ?: return@transaction LoginResult(null, null) // Пользователь не найден

                val userId = row[AuthUsersTable.id].value
                val storedUsername = row[AuthUsersTable.username] // Получаем точное имя из БД
                val hash = row[AuthUsersTable.passwordHash]

                // Проверяем пароль с помощью BCrypt
                if (BCrypt.checkpw(password, hash)) {
                    // Пароль верный, генерируем токен
                    val token = tokenService.createToken(userId, storedUsername)
                    // Создаем "заглушку" профиля
                    val profileStub = NatsUserProfileStub(id = userId.toString(), username = storedUsername)
                    LoginResult(token, profileStub)
                } else {
                    LoginResult(null, null) // Неверный пароль
                }
            }
        } catch (e: Exception) {
            log.error("Unexpected error during login for user '{}': {}", username, e.message, e)
            LoginResult(null, null)
        }
    }
}