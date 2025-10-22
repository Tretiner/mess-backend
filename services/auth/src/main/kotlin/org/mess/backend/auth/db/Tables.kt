package org.mess.backend.auth.db

import org.jetbrains.exposed.dao.id.UUIDTable

// Таблица пользователей для АУТЕНТИФИКАЦИИ
object AuthUsersTable : UUIDTable("t_auth_users") {
    val username = varchar("username", 50).uniqueIndex() // Уникальный логин
    val passwordHash = varchar("password_hash", 255) // Хеш пароля
}
// Таблица RefreshTokensTable удалена.