package org.mess.backend.chat.services

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.chat.db.ChatMembersTable
import org.mess.backend.chat.db.ChatsTable
import org.mess.backend.chat.db.MessagesTable
import org.mess.backend.chat.models.*
import java.util.*

class ChatService(
    private val profileService: RemoteProfileService // Внедряем NATS-клиент
) {

    // --- Обработка входящих сообщений ---

    /**
     * Вызывается из `chat.message.incoming`
     */
    suspend fun processIncomingMessage(msg: NatsIncomingMessage): NatsBroadcastMessage? {
        // 1. Получаем профиль отправителя (NATS-запрос к user-service)
        val senderProfile = profileService.getProfile(UUID.fromString(msg.userId))

        val messageId = UUID.randomUUID()
        val timestamp = Clock.System.now()

        // 2. Сохраняем сообщение в БД
        transaction {
            MessagesTable.insert {
                it[id] = messageId
                it[chatId] = UUID.fromString(msg.chatId)
                it[senderId] = UUID.fromString(msg.userId)
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

    /**
     * Вызывается из `chat.create.group`
     */
    suspend fun createGroupChat(request: NatsChatCreateGroupRequest): NatsChat {
        val allMemberUUIDs = (request.memberIds.map { UUID.fromString(it) } + UUID.fromString(request.creatorId)).distinct()

        val newChatId = transaction {
            // 1. Создаем чат
            val newChatId = ChatsTable.insert {
                it[name] = request.name
                it[isGroup] = true
            } get ChatsTable.id

            // 2. Добавляем участников
            ChatMembersTable.batchInsert(allMemberUUIDs) { userId ->
                this[ChatMembersTable.chatId] = newChatId.value
                this[ChatMembersTable.userId] = userId
            }
            newChatId.value
        }

        // 3. Возвращаем полные детали (с NATS-запросами к user-service)
        return getChatDetails(newChatId)
    }

    /**
     * Вызывается из `chat.create.dm`
     */
    suspend fun createDirectMessageChat(request: NatsChatCreateDmRequest): NatsChat {
        val userUUID1 = UUID.fromString(request.userId1)
        val userUUID2 = UUID.fromString(request.userId2)

        // 1. Ищем существующий DM
        val existingChatId = transaction {
            (ChatMembersTable innerJoin ChatsTable)
                .slice(ChatMembersTable.chatId)
                .select {
                    (ChatsTable.isGroup eq false) and
                            ((ChatMembersTable.userId eq userUUID1) or (ChatMembersTable.userId eq userUUID2))
                }
                .groupBy(ChatMembersTable.chatId)
                .having { count(ChatMembersTable.userId) eq 2 }
                .map { it[ChatMembersTable.chatId] }
                .firstOrNull()?.value
        }

        if (existingChatId != null) {
            return getChatDetails(existingChatId)
        }

        // 2. Создаем новый DM
        val newChatId = transaction {
            val newChatId = ChatsTable.insert {
                it[name] = null
                it[isGroup] = false
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
     * Вызывается из `chat.get.mychats`
     */
    suspend fun getMyChats(userId: String): NatsGetMyChatsResponse {
        val userUUID = UUID.fromString(userId)

        // 1. Находим все ID чатов, в которых состоит юзер
        val chatIds = transaction {
            ChatMembersTable
                .select { ChatMembersTable.userId eq userUUID }
                .map { it[ChatMembersTable.chatId].value }
        }

        // 2. Получаем детали для *каждого* чата (N+1 NATS-запросы)
        val chats = coroutineScope {
            chatIds.map { async { getChatDetails(it) } }.awaitAll()
        }

        return NatsGetMyChatsResponse(chats)
    }


    /**
     * Хелпер: Получает детали чата (N+1 запросов к user-service)
     */
    private suspend fun getChatDetails(chatId: UUID): NatsChat {
        // 1. Получаем инфо о чате и ID участников (из *нашей* БД)
        val (chat, memberIds) = transaction {
            val chat = ChatsTable.select { ChatsTable.id eq chatId }.first()
            val memberIds = ChatMembersTable
                .select { ChatMembersTable.chatId eq chatId }
                .map { it[ChatMembersTable.userId] }

            chat to memberIds
        }

        // 2. Получаем профили участников (N+1 NATS-запросов к user-service)
        // TODO: Оптимизировать это в один batch-запрос `user.profiles.get.batch`
        val members = coroutineScope {
            memberIds.map { async { profileService.getProfile(it) } }.awaitAll()
        }

        return NatsChat(
            id = chat[ChatsTable.id].value.toString(),
            name = chat[ChatsTable.name],
            isGroup = chat[ChatsTable.isGroup],
            members = members
        )
    }
}