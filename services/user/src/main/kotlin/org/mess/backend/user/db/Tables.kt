package org.mess.backend.user.db

import org.jetbrains.exposed.dao.id.UUIDTable

// Таблица Профилей
// ID здесь - это Primary Key, НО он *не* генерируется автоматически.
// Мы получаем его из события `user.created`
object UserProfilesTable : UUIDTable("t_user_profiles") {
    // Мы не используем .autoGenerate()
    // id (из UUIDTable) является PK

    val nickname = varchar("nickname", 50)
    val avatarUrl = varchar("avatar_url", 255).nullable()

    // createdAt / updatedAt можно добавить по желанию
}