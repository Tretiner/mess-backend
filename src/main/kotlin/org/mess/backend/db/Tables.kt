package org.mess.backend.db

import org.ktorm.schema.*

object UsersTable : Table<Nothing>("t_users") {
    // Мы не используем .primaryKey() в определении,
    // так как PK создается в raw SQL в 'Database.kt'
    val id = uuid("id")
    val username = varchar("username")
    val passwordHash = varchar("password_hash")
    val nickname = varchar("nickname")
    val avatarUrl = varchar("avatar_url")
    val createdAt = timestamp("created_at")
}

object ChatsTable : Table<Nothing>("t_chats") {
    val id = uuid("id")
    val name = varchar("name")
    val isGroup = boolean("is_group")
    val createdAt = timestamp("created_at")
}

object ChatMembersTable : Table<Nothing>("t_chat_members") {
    val id = uuid("id") // Вспомогательный PK
    val chatId = uuid("chat_id")
    val userId = uuid("user_id")
    val joinedAt = timestamp("joined_at")
}

object MessagesTable : Table<Nothing>("t_messages") {
    val id = uuid("id")
    val chatId = uuid("chat_id")
    val senderId = uuid("sender_id")
    val type = varchar("type")
    val content = text("content")
    val sentAt = timestamp("sent_at")
}