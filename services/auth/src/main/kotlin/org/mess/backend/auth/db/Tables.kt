package org.mess.backend.auth.db

import org.jetbrains.exposed.dao.id.UUIDTable

object AuthUsersTable : UUIDTable("t_auth_users") {
    val username = varchar("username", 50).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    // Никнейм, аватар и т.д. в `user-service`
}