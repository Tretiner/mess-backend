package org.mess.backend.auth.db

import org.jetbrains.exposed.dao.id.UUIDTable

internal object AuthUsersTable : UUIDTable("t_auth_users") {
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255) // Хеш пароля (bcrypt)
}