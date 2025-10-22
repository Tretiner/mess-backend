package org.mess.backend.chat.db

import kotlinx.datetime.Clock
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

// Таблица чатов
object ChatsTable : UUIDTable("t_chats") {
    val name = varchar("name", 100).nullable()
    val isGroup = bool("is_group").default(false)
    // ID пользователя, создавшего чат (для прав админа)
    val creatorId = uuid("creator_id").nullable()
    // URL аватара для групповых чатов
    val chatAvatarUrl = varchar("chat_avatar_url", 255).nullable()
}

// Таблица участников
object ChatMembersTable : UUIDTable("t_chat_members") {
    val chatId = reference("chat_id", ChatsTable, onDelete = ReferenceOption.CASCADE)
    val userId = uuid("user_id") // ID участника

    init {
        uniqueIndex(chatId, userId)
    }
}

// Таблица сообщений
object MessagesTable : UUIDTable("t_messages") {
    val chatId = reference("chat_id", ChatsTable, onDelete = ReferenceOption.CASCADE)
    val senderId = uuid("sender_id") // ID отправителя
    val type = varchar("type", 20).default("text")
    val content = text("content")
    val sentAt = timestamp("sent_at").default(Clock.System.now())
}