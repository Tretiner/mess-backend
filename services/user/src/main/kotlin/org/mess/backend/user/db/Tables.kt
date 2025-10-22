// FILE: services/user/src/main/kotlin/org/mess/backend/user/db/Tables.kt
package org.mess.backend.user.db

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object UserProfilesTable : UUIDTable("t_user_profiles") {
    val username = varchar("username", 50).uniqueIndex() // <-- RENAMED from nickname, added unique constraint
    val avatarUrl = varchar("avatar_url", 255).nullable()
    val email = varchar("email", 255).nullable().uniqueIndex()
    val fullName = varchar("full_name", 100).nullable()
    val createdAt = timestamp("created_at").default(Clock.System.now())
    val updatedAt = timestamp("updated_at").default(Clock.System.now())
}