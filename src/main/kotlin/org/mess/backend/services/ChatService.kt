package org.mess.backend.services

import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.db.ChatMembersTable
import org.mess.backend.db.ChatsTable
import org.mess.backend.db.MessagesTable
import org.mess.backend.db.UsersTable
import org.mess.backend.models.*
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class ChatService(
    private val nats: NatsService,
    private val userService: UserService
) {

    private val activeSessions = ConcurrentHashMap<String, WebSocketSession>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun userConnected(userId: String, session: WebSocketSession) {
        println("User connected: $userId")
        activeSessions[userId] = session
    }

    fun userDisconnected(userId: String) {
        println("User disconnected: $userId")
        activeSessions.remove(userId)
    }

    // --- ЛОГИКА: REST API для Чатов ---

    private fun getChatDetails(chatId: UUID): ChatResponse? {
        return transaction {
            val chat = ChatsTable.select { ChatsTable.id eq chatId }.firstOrNull() ?: return@transaction null

            val members = (ChatMembersTable innerJoin UsersTable)
                .slice(UsersTable.id, UsersTable.nickname)
                .select { ChatMembersTable.chatId eq chatId }
                .map {
                    ChatMember(
                        id = it[UsersTable.id].value.toString(),
                        nickname = it[UsersTable.nickname]
                    )
                }

            ChatResponse(
                id = chat[ChatsTable.id].value.toString(),
                name = chat[ChatsTable.name],
                isGroup = chat[ChatsTable.isGroup],
                members = members
            )
        }
    }

    fun createGroupChat(creatorId: String, name: String, memberIds: List<String>): ChatResponse {
        return transaction {
            val creatorUUID = UUID.fromString(creatorId)
            val memberUUIDs = memberIds.map { UUID.fromString(it) }
            val allMemberUUIDs = (memberUUIDs + creatorUUID).distinct()

            // 1. Создаем чат
            val newChatId = ChatsTable.insert {
                it[ChatsTable.name] = name
                it[isGroup] = true
            } get ChatsTable.id

            // 2. Добавляем всех участников
            ChatMembersTable.batchInsert(allMemberUUIDs) { userId ->
                this[ChatMembersTable.chatId] = newChatId
                this[ChatMembersTable.userId] = userId
            }

            // 3. Возвращаем полную информацию о чате
            getChatDetails(newChatId.value)!!
        }
    }

    fun <T : Any> wrapAsExpressionWithColumnType(query: AbstractQuery<*>, columnType: IColumnType) =
        object : ExpressionWithColumnType<T?>() {
            private val expression = wrapAsExpression<T>(query)

            override fun toQueryBuilder(queryBuilder: QueryBuilder) = expression.toQueryBuilder(queryBuilder)
            override val columnType: IColumnType = columnType
        }

    fun createDirectMessageChat(userId1: String, userId2: String): ChatResponse {
        val userUUID1 = UUID.fromString(userId1)
        val userUUID2 = UUID.fromString(userId2)

        return transaction {
            val existingChatId = ChatMembersTable.slice(ChatMembersTable.chatId)
                .select { (ChatMembersTable.userId eq userUUID1) or (ChatMembersTable.userId eq userUUID2) }
                .groupBy(ChatMembersTable.chatId)
                .having { wrapAsExpressionWithColumnType<>(ChatMembersTable.userId) }
                .map { it[ChatMembersTable.chatId] }
                .firstOrNull { chatId ->
                    !ChatsTable.select { (ChatsTable.id eq chatId) and (ChatsTable.isGroup eq false) }
                        .limit(1)
                        .empty()
                }

            if (existingChatId != null) {
                // 2. Если чат уже есть, возвращаем его
                return@transaction getChatDetails(existingChatId.value)!!
            }

            // 3. Если чата нет, создаем новый DM
            val newChatId = ChatsTable.insert {
                it[name] = null
                it[isGroup] = false
            } get ChatsTable.id

            ChatMembersTable.batchInsert(listOf(userUUID1, userUUID2)) { userId ->
                this[ChatMembersTable.chatId] = newChatId
                this[ChatMembersTable.userId] = userId
            }

            getChatDetails(newChatId.value)!!
        }
    }

    fun addUserToChat(chatId: String, userIdToAdd: String, addedByUserId: String): ChatResponse? {
        val chatUUID = UUID.fromString(chatId)
        val userUUID = UUID.fromString(userIdToAdd)
        val adderUUID = UUID.fromString(addedByUserId)

        return transaction {
            // TODO: Проверить, что addedByUserId (adderUUID) вообще состоит в этом чате

            // Проверяем, что чат существует и это группа
            val chat = ChatsTable.select { (ChatsTable.id eq chatUUID) and (ChatsTable.isGroup eq true) }.firstOrNull()
                ?: return@transaction null // Нельзя добавить в DM или чат не найден

            // Добавляем, если его там еще нет
            ChatMembersTable.insertIgnore {
                it[ChatMembersTable.chatId] = chatUUID
                it[ChatMembersTable.userId] = userUUID
            }

            getChatDetails(chatUUID)
        }
    }


    // --- ЛОГИКА: WebSocket ---

    fun sendMessageToNats(senderId: String, incomingJson: String) {
        try {
            val incomingMsg = json.decodeFromString<IncomingWsMessage>(incomingJson)

            val senderProfile = runBlocking { userService.getUserProfile(senderId) } ?: return

            val messageUUID = UUID.randomUUID()
            val chatUUID = UUID.fromString(incomingMsg.chatId)
            val timestamp = Clock.System.now()

            // 2. Сохраняем сообщение в БД
            transaction {
                MessagesTable.insert {
                    it[id] = messageUUID
                    it[chatId] = chatUUID
                    it[MessagesTable.senderId] = UUID.fromString(senderId)
                    it[type] = incomingMsg.type
                    it[content] = incomingMsg.content
                    it[sentAt] = timestamp
                }
            }

            // 3. Создаем полную модель ChatMessage для рассылки
            val chatMessage = ChatMessage(
                messageId = messageUUID.toString(),
                chatId = incomingMsg.chatId,
                senderId = senderId,
                senderNickname = senderProfile.nickname,
                type = incomingMsg.type,
                content = incomingMsg.content,
                sentAt = timestamp
            )

            val messageJson = json.encodeToString(chatMessage)
            nats.publishMessage(messageJson)

        } catch (e: Exception) {
            println("Failed to process incoming WS message: ${e.message}")
        }
    }

    suspend fun broadcastMessageToLocalClients(messageJson: String) {
        try {
            val message = json.decodeFromString<ChatMessage>(messageJson)

            // 1. Найти всех ID пользователей, которые состоят в этом чате
            val recipientIds = findRecipientsForChat(UUID.fromString(message.chatId))

            // 2. Пройтись по всем получателям
            for (userId in recipientIds) {
                // 3. Если получатель подключен к ЭТОМУ инстансу...
                activeSessions[userId]?.let { session ->
                    try {
                        session.send(Frame.Text(messageJson))
                    } catch (e: Exception) {
                        println("Failed to send to $userId: ${e.message}")
                        activeSessions.remove(userId, session) // Удаляем мертвую сессию
                    }
                }
            }

        } catch (e: Exception) {
            println("Failed to broadcast NATS message: ${e.message}")
        }
    }

    private fun findRecipientsForChat(chatId: UUID): List<String> {
        return transaction {
            ChatMembersTable.slice(ChatMembersTable.userId)
                .select { ChatMembersTable.chatId eq chatId }
                .map { it[ChatMembersTable.userId].value.toString() }
        }
    }
}