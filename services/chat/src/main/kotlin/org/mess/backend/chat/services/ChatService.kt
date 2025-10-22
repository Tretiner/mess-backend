package org.mess.backend.chat.services

import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.mess.backend.chat.db.ChatMembersTable
import org.mess.backend.chat.db.ChatsTable
import org.mess.backend.chat.db.MessagesTable
import org.mess.backend.chat.models.*
import java.util.*

class ChatService(
    private val profileService: RemoteProfileService
) {

    // --- Обработка входящих сообщений ---

    suspend fun processIncomingMessage(msg: NatsIncomingMessage): NatsBroadcastMessage? {
        val senderUUID = UUID.fromString(msg.userId)
        val chatUUID = UUID.fromString(msg.chatId)

        // 1. Получаем профиль отправителя
        // В реальном приложении здесь должен быть кэш, но для простоты делаем запрос
        val senderProfile = profileService.getProfile(senderUUID)
        val messageId = UUID.randomUUID()
        val timestamp = Clock.System.now()

        // 2. Сохраняем сообщение в БД
        newSuspendedTransaction {
            MessagesTable.insert {
                it[id] = messageId
                it[chatId] = chatUUID
                it[senderId] = senderUUID
                it[type] = msg.type
                it[content] = msg.content
                it[sentAt] = timestamp
            }
        }

        // 3. Создаем сообщение для бродкаста
        return NatsBroadcastMessage(
            messageId = messageId.toString(),
            chatId = msg.chatId,
            sender = senderProfile,
            type = msg.type,
            content = msg.content,
            sentAt = timestamp
        )
    }

    // --- CRUD для Чатов ---

    suspend fun createGroupChat(request: NatsChatCreateGroupRequest): NatsChat {
        val creatorUUID = UUID.fromString(request.creatorId)
        val allMemberUUIDs = (request.memberIds.map { UUID.fromString(it) } + creatorUUID).distinct()

        val newChatId = newSuspendedTransaction {
            // 1. Создаем чат
            val newChatId = ChatsTable.insert {
                it[name] = request.name
                it[isGroup] = true
                it[creatorId] = creatorUUID
                it[chatAvatarUrl] = request.avatarUrl
            } get ChatsTable.id

            // 2. Добавляем участников
            ChatMembersTable.batchInsert(allMemberUUIDs) { userId ->
                this[ChatMembersTable.chatId] = newChatId.value
                this[ChatMembersTable.userId] = userId
            }
            newChatId.value
        }

        // 3. Возвращаем полные детали
        return getChatDetails(newChatId)
    }

    suspend fun createDirectMessageChat(request: NatsChatCreateDmRequest): NatsChat {
        val userUUID1 = UUID.fromString(request.userId1)
        val userUUID2 = UUID.fromString(request.userId2)

        // 1. Ищем существующий DM
        val existingChatId = newSuspendedTransaction {
            (ChatMembersTable innerJoin ChatsTable)
                .slice(ChatMembersTable.chatId)
                .selectAll().where {
                    (ChatsTable.isGroup eq false) and
                            ((ChatMembersTable.userId eq userUUID1) or (ChatMembersTable.userId eq userUUID2))
                }
                .groupBy(ChatMembersTable.chatId)
                .having { ChatMembersTable.userId.count() eq 2 }
                .map { it[ChatMembersTable.chatId] }
                .firstOrNull()?.value
        }

        if (existingChatId != null) {
            return getChatDetails(existingChatId)
        }

        // 2. Создаем новый DM
        val newChatId = newSuspendedTransaction {
            val newChatId = ChatsTable.insert {
                it[name] = null // DM не имеют имени
                it[isGroup] = false
                it[creatorId] = null // DM не имеют админа
            } get ChatsTable.id

            ChatMembersTable.batchInsert(listOf(userUUID1, userUUID2)) { userId ->
                this[ChatMembersTable.chatId] = newChatId.value
                this[ChatMembersTable.userId] = userId
            }
            newChatId.value
        }

        return getChatDetails(newChatId)
    }

    /**
     * Оптимизированный метод. Получает все чаты, всех участников и все последние сообщения,
     * а затем делает 1 batch-запрос к user-service.
     */
    /**
     * Оптимизированный метод. Получает все чаты, всех участников и все последние сообщения,
     * а затем делает 1 batch-запрос к user-service.
     */
    suspend fun getMyChats(userId: String): NatsGetMyChatsResponse {
        val userUUID = UUID.fromString(userId)

        val chatsMap = mutableMapOf<UUID, NatsChatData>()

        newSuspendedTransaction {
            // 1. Получаем список всех чатов, в которых участвует пользователь (JOIN)
            (ChatMembersTable innerJoin ChatsTable)
                .select { ChatMembersTable.userId eq userUUID }
                .forEach { row ->
                    val chatId = row[ChatsTable.id].value
                    // В row есть все колонки из обеих таблиц благодаря innerJoin
                    chatsMap[chatId] = NatsChatData(chatRow = row)
                }

            if (chatsMap.isEmpty()) return@newSuspendedTransaction

            val allChatIds = chatsMap.keys.toList()

            // 2. Получаем всех участников для всех найденных чатов (за один запрос)
            ChatMembersTable
                .select { ChatMembersTable.chatId inList allChatIds }
                .forEach { row ->
                    val chatId = row[ChatMembersTable.chatId].value
                    chatsMap[chatId]?.memberIds?.add(row[ChatMembersTable.userId])
                }

            // 3. Получаем последние сообщения (N запросов, но быстро)
            chatsMap.keys.forEach { chatId ->
                val lastMsgRow = MessagesTable
                    .select { MessagesTable.chatId eq chatId }
                    .orderBy(MessagesTable.sentAt to SortOrder.DESC)
                    .limit(1)
                    .firstOrNull()
                chatsMap[chatId]?.lastMsgRow = lastMsgRow
            }
        } // Конец транзакции

        // 4. Собираем ВСЕХ уникальных ID для NATS-запроса
        val allUserIds = mutableSetOf<UUID>()
        chatsMap.values.forEach { chatData ->
            allUserIds.addAll(chatData.memberIds)
            chatData.lastMsgRow?.get(MessagesTable.senderId)?.let { allUserIds.add(it) }
        }

        // 5. Делаем ОДИН NATS-запрос
        val profilesMap = coroutineScope { profileService.getProfilesBatch(allUserIds) }

        // 6. Собираем DTO
        val chats = chatsMap.values.map { chatData ->
            // chatRow содержит данные ChatTable, которые мы маппили в NatsChatData
            val chatRow = chatData.chatRow
            // Используем хелпер для маппинга данных из ResultRow
            val chat = chatRow.toChat()

            NatsChat(
                id = chat.id.value.toString(),
                name = chat.name,
                isGroup = chat.isGroup,
                creatorId = chat.creatorId?.toString(),
                chatAvatarUrl = chat.chatAvatarUrl,
                members = chatData.memberIds.mapNotNull { profilesMap[it] },
                lastMessage = chatData.lastMsgRow?.let { lastMsgRow ->
                    val sender = profilesMap[lastMsgRow[MessagesTable.senderId]]
                    NatsLastMessage(
                        content = lastMsgRow[MessagesTable.content],
                        senderName = sender?.username ?: "Unknown",
                        timestamp = lastMsgRow[MessagesTable.sentAt]
                    )
                }
            )
        }

        return NatsGetMyChatsResponse(chats)
    }

    /**
     * Получает детали ОДНОГО чата. Используется клиентом.
     */
    suspend fun getChatDetails(request: NatsGetChatDetailsRequest): NatsChat {
        return getChatDetails(UUID.fromString(request.chatId))
    }

    /**
     * Обновляет детали чата (только админ).
     */
    suspend fun updateChatDetails(request: NatsUpdateChatRequest): NatsChat {
        val chatUUID = UUID.fromString(request.chatId)
        val userUUID = UUID.fromString(request.requestedByUserId)

        val updatedCount = newSuspendedTransaction {
            ChatsTable.update(
                where = { (ChatsTable.id eq chatUUID) and (ChatsTable.creatorId eq userUUID) }
            ) {
                // Обновляем, только если поля не null
                request.newName?.let { newName -> it[name] = newName }
                request.newAvatarUrl?.let { newAvatar -> it[chatAvatarUrl] = newAvatar }
            }
        }

        if (updatedCount == 0) {
            throw SecurityException("User $userUUID is not the admin of chat $chatUUID or chat does not exist.")
        }

        return getChatDetails(chatUUID)
    }

    /**
     * Удаляет пользователя из чата (только админ).
     */
    suspend fun removeUserFromChat(request: NatsRemoveUserRequest) {
        val chatUUID = UUID.fromString(request.chatId)
        val adminUUID = UUID.fromString(request.requestedByUserId)
        val userToRemoveUUID = UUID.fromString(request.userIdToRemove)

        val chat = newSuspendedTransaction {
            ChatsTable.selectAll().where { ChatsTable.id eq chatUUID }.firstOrNull()?.toChat()
        } ?: throw IllegalStateException("Chat not found")

        if (chat.creatorId != adminUUID) {
            throw SecurityException("User $adminUUID is not the admin.")
        }
        if (chat.creatorId == userToRemoveUUID) {
            throw IllegalStateException("Admin cannot remove themselves.")
        }

        newSuspendedTransaction {
            ChatMembersTable.deleteWhere {
                (ChatMembersTable.chatId eq chatUUID) and (ChatMembersTable.userId eq userToRemoveUUID)
            }
        }
    }


    // --- Приватные хелперы ---

    /**
     * Внутренний хелпер: Получает полные детали чата по ID.
     */
    private suspend fun getChatDetails(chatId: UUID): NatsChat = coroutineScope {
        val (chat, memberIds, lastMsgRow) = newSuspendedTransaction {
            val chat = ChatsTable.selectAll().where { ChatsTable.id eq chatId }.first().toChat()
            val memberIds = ChatMembersTable
                .selectAll().where { ChatMembersTable.chatId eq chatId }
                .map { it[ChatMembersTable.userId] }

            val lastMsg = MessagesTable
                .selectAll().where { MessagesTable.chatId eq chatId }
                .orderBy(MessagesTable.sentAt to SortOrder.DESC)
                .limit(1)
                .firstOrNull()

            Triple(chat, memberIds, lastMsg)
        }

        // Собираем ID для batch-запроса
        val userIdsToFetch = memberIds.toMutableSet()
        lastMsgRow?.get(MessagesTable.senderId)?.let { userIdsToFetch.add(it) }

        val profilesMap = profileService.getProfilesBatch(userIdsToFetch)

        NatsChat(
            id = chat.id.value.toString(),
            name = chat.name,
            isGroup = chat.isGroup,
            creatorId = chat.creatorId?.toString(),
            chatAvatarUrl = chat.chatAvatarUrl,
            members = memberIds.mapNotNull { profilesMap[it] },
            lastMessage = lastMsgRow?.let {
                val sender = profilesMap[it[MessagesTable.senderId]]
                NatsLastMessage(
                    content = it[MessagesTable.content],
                    senderName = sender?.username ?: "Unknown",
                    timestamp = it[MessagesTable.sentAt]
                )
            }
        )
    }

    // Хелпер для маппинга ResultRow в DTO (чтобы избежать дублирования)
    private fun ResultRow.toChat() = Chat(
        id = this[ChatsTable.id],
        name = this[ChatsTable.name],
        isGroup = this[ChatsTable.isGroup],
        creatorId = this[ChatsTable.creatorId],
        chatAvatarUrl = this[ChatsTable.chatAvatarUrl]
    )

    // Внутренняя DTO, только для этого сервиса
    private data class Chat(
        val id: org.jetbrains.exposed.dao.id.EntityID<UUID>,
        val name: String?,
        val isGroup: Boolean,
        val creatorId: UUID?,
        val chatAvatarUrl: String?
    )
}