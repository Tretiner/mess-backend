package org.mess.backend.chat.models

// Внутренняя DTO, только для этого сервиса
private data class Chat(
    val id: org.jetbrains.exposed.dao.id.EntityID<java.util.UUID>,
    val name: String?,
    val isGroup: Boolean,
    val creatorId: java.util.UUID?,
    val chatAvatarUrl: String?
)

// Внутренняя DTO для сборки данных
data class NatsChatData(
    val chatRow: org.jetbrains.exposed.sql.ResultRow,
    val memberIds: MutableList<java.util.UUID> = mutableListOf(),
    var lastMsgRow: org.jetbrains.exposed.sql.ResultRow? = null
)

// Хелпер для маппинга ResultRow в DTO
private fun org.jetbrains.exposed.sql.ResultRow.toChat() = Chat(
    id = this[org.mess.backend.chat.db.ChatsTable.id],
    name = this[org.mess.backend.chat.db.ChatsTable.name],
    isGroup = this[org.mess.backend.chat.db.ChatsTable.isGroup],
    creatorId = this[org.mess.backend.chat.db.ChatsTable.creatorId],
    chatAvatarUrl = this[org.mess.backend.chat.db.ChatsTable.chatAvatarUrl]
)