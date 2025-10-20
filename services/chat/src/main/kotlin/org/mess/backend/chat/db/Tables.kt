package org.mess.backend.chat.db

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import kotlinx.datetime.Instant

// Таблица чатов
object ChatsTable : UUIDTable("t_chats") {
    val name = varchar("name", 100).nullable()
    val isGroup = bool("is_group").default(false)
}

// Таблица участников
object ChatMembersTable : UUIDTable("t_chat_members") {
    val chatId = reference("chat_id", ChatsTable, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id") // Мы не можем ссылаться на `user-service`

    init {
        uniqueIndex(chatId, userId)
    }
}

// Таблица сообщений
object MessagesTable : UUIDTable("t_messages") {
    val chatId = reference("chat_id", ChatsTable, onDelete = ReferenceOption.CASCADE)
    val senderId = uuid("sender_id") // Мы не можем ссылаться на `user-service`
    val type = varchar("type", 20).default("text")
    val content = text("content")
    val sentAt = timestamp("sent_at").default(Clock.System.now())
}