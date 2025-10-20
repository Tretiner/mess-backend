package org.mess.backend.services

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.mess.backend.db.UsersTable
import org.mess.backend.models.UserProfile
import org.mess.backend.models.UserProfileUpdateRequest
import org.mindrot.jbcrypt.BCrypt
import java.util.*

class UserService {

    // Конвертер из ResultRow (БД) в нашу модель (JSON)
    private fun toUserProfile(row: ResultRow) = UserProfile(
        id = row[UsersTable.id].value.toString(),
        nickname = row[UsersTable.nickname],
        avatarUrl = row[UsersTable.avatarUrl]
    )

    fun registerUser(username: String, password: String): UserProfile? {
        return transaction {
            // 1. Проверяем, не занят ли username
            val existing = UsersTable.select { UsersTable.username eq username }.count()
            if (existing > 0) {
                return@transaction null // Пользователь занят
            }

            // 2. Хэшируем пароль
            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            // 3. Создаем пользователя
            val insertedRow = UsersTable.insert {
                it[id] = UUID.randomUUID()
                it[UsersTable.username] = username
                it[UsersTable.passwordHash] = passwordHash
                it[nickname] = username // По умолчанию ник = username
                it[avatarUrl] = null
            }
            toUserProfile(insertedRow.resultedValues!!.first())
        }
    }

    fun loginUser(username: String, password: String): UserProfile? {
        return transaction {
            val row = UsersTable.select { UsersTable.username eq username }.firstOrNull() ?: return@transaction null

            if (BCrypt.checkpw(password, row[UsersTable.passwordHash])) {
                toUserProfile(row)
            } else {
                null // Неверный пароль
            }
        }
    }

    fun getUserProfile(userId: String): UserProfile? {
        return transaction {
            UsersTable.select { UsersTable.id eq UUID.fromString(userId) }
                .firstOrNull()
                ?.let { toUserProfile(it) }
        }
    }

    fun updateUserProfile(userId: String, newNickname: String?, newAvatarUrl: String?): UserProfile? {
        return transaction {
            UsersTable.update({ UsersTable.id eq UUID.fromString(userId) }) {
                newNickname?.let { nn -> it[nickname] = nn }
                newAvatarUrl?.let { av -> it[avatarUrl] = av }
            }
            // Возвращаем обновленный профиль
            getUserProfile(userId)
        }
    }
    
    fun searchUsers(query: String): List<UserProfile> {
        return transaction {
            UsersTable.select { UsersTable.nickname like "%$query%" }
                .limit(20)
                .map { toUserProfile(it) }
        }
    }
}