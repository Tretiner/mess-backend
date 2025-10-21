package org.mess.backend.user.db

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Instant

object UserProfilesTable : UUIDTable("t_user_profiles") {
    // id (из UUIDTable) является PK, получаем его из auth-service

    val nickname = varchar("nickname", 50)
    val avatarUrl = varchar("avatar_url", 255).nullable()
    // --- НОВЫЕ ПОЛЯ ---
    val email = varchar("email", 255).nullable().uniqueIndex() // Email должен быть уникальным (или null)
    val fullName = varchar("full_name", 100).nullable()
    // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}