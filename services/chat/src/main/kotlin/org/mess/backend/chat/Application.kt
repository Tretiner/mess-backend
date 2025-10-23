package org.mess.backend.chat

import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.mess.backend.chat.db.initDatabase
import org.mess.backend.chat.models.*
import org.mess.backend.chat.services.ChatService
import org.mess.backend.chat.services.RemoteProfileService
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.decodeFromBytes
import org.mess.backend.core.encodeToBytes
import java.nio.charset.StandardCharsets

val serviceScope = CoroutineScope(Dispatchers.IO)
val json: Json = DefaultJson

fun main() {
    val config = Config.load()
    println("Config loaded. Connecting to NATS at ${config.natsUrl}")

    val nats: Connection = Nats.connect(config.natsUrl)
    println("NATS connected.")

    val remoteProfileService = RemoteProfileService(nats)
    val chatService = ChatService(remoteProfileService)

    initDatabase(config.db)
    println("Database connected. Awaiting requests...")

    // --- СЛУШАТЕЛЬ 1: `chat.message.incoming` (Pub/Sub) ---
    nats.createDispatcher { msg ->
        println(">>> Chat Service (Dispatcher): Received NATS message on subject: ${msg.subject}")
        serviceScope.launch {
            try {
                println(">>> Chat Service (Coroutine): Starting processing for incoming message...")
                val incomingMsg = json.decodeFromBytes<NatsIncomingMessage>(msg.data)
                println(">>> Chat Service (Coroutine): Parsed incoming message from user ${incomingMsg.userId} for chat ${incomingMsg.chatId}")

                val broadcastMessage = chatService.processIncomingMessage(incomingMsg)

                println(">>> Chat Service (Coroutine): processIncomingMessage completed. Result: ${if (broadcastMessage != null) "Message to broadcast" else "null"}")

                if (broadcastMessage != null) {
                    // 1. Get chat members (requires a DB query or modification to processIncomingMessage)
                    val memberIds = chatService.getChatMemberIds(broadcastMessage.chatId) // <-- NEW FUNCTION NEEDED in ChatService

                    // 2. Publish to EACH member's topic
                    memberIds.forEach { memberId ->
                        val userTopic = "chat.broadcast.$memberId"
                        println(">>> Chat Service (Coroutine): Publishing message ID ${broadcastMessage.messageId} to '$userTopic'")
                        nats.publish(userTopic, json.encodeToBytes(broadcastMessage))
                    }
                    println(">>> Chat Service (Coroutine): Published message to relevant user topics.")
                }
            } catch (e: Exception) {
                println("!!! Chat Service (Coroutine): Error processing 'chat.message.incoming': ${e.message}")
                e.printStackTrace()
            }
        }
    }.subscribe("chat.message.incoming")

    // --- СЛУШАТЕЛЬ 2: `chat.create.group` (Request-Reply) ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val response = try {
                val request = json.decodeFromBytes<NatsChatCreateGroupRequest>(msg.data)
                val chat = chatService.createGroupChat(request)
                json.encodeToBytes(chat)
            } catch (e: Exception) {
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, response)
        }
    }.subscribe("chat.create.group")

    // --- СЛУШАТЕЛЬ 3: `chat.create.dm` (Request-Reply) ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val response = try {
                val request = json.decodeFromBytes<NatsChatCreateDmRequest>(msg.data)
                val chat = chatService.createDirectMessageChat(request)
                json.encodeToBytes(chat)
            } catch (e: Exception) {
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, response)
        }
    }.subscribe("chat.create.dm")

    // --- СЛУШАТЕЛЬ 4: `chat.get.mychats` (Request-Reply - ОПТИМИЗИРОВАН) ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val response = try {
                val request = json.decodeFromBytes<NatsGetMyChatsRequest>(msg.data)
                val response = chatService.getMyChats(request.userId)
                json.encodeToBytes(response)
            } catch (e: Exception) {
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, response)
        }
    }.subscribe("chat.get.mychats")

    // --- НОВЫЙ СЛУШАТЕЛЬ 5: `chat.get.details` ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val response = try {
                val request = json.decodeFromBytes<NatsGetChatDetailsRequest>(msg.data)
                val chat = chatService.getChatDetails(request)
                json.encodeToBytes(chat)
            } catch (e: Exception) {
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, response)
        }
    }.subscribe("chat.get.details")

    // --- НОВЫЙ СЛУШАТЕЛЬ 6: `chat.update.details` (Админ) ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val response = try {
                val request = json.decodeFromBytes<NatsUpdateChatRequest>(msg.data)
                val chat = chatService.updateChatDetails(request)
                json.encodeToBytes(chat)
            } catch (e: Exception) {
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, response)
        }
    }.subscribe("chat.update.details")

    // --- НОВЫЙ СЛУШАТЕЛЬ 7: `chat.remove.user` (Админ) ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val response = try {
                val request = json.decodeFromBytes<NatsRemoveUserRequest>(msg.data)
                chatService.removeUserFromChat(request)
                // Отправляем простой "ok" или пустой ответ
                "{}".toByteArray(StandardCharsets.UTF_8)
            } catch (e: Exception) {
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, response)
        }
    }.subscribe("chat.remove.user")

    // --- NEW LISTENER 8: `chat.messages.get` (Request-Reply) ---
    nats.createDispatcher { msg ->
        serviceScope.launch {
            val responseBytes = try {
                val request = json.decodeFromBytes<NatsMessagesGetRequest>(msg.data)
                println(">>> Chat Service: Received message history request for chat ${request.chatId}")
                val response = chatService.getMessagesForChat(request)
                println(">>> Chat Service: Sending ${response.messages.size} messages for chat ${request.chatId}")
                json.encodeToBytes(response)
            } catch (e: SecurityException) {
                println("!!! Chat Service: Access denied for history request: ${e.message}")
                json.encodeToBytes(NatsErrorResponse("Access Denied: ${e.message}"))
            } catch (e: Exception) {
                println("!!! Chat Service: Error processing chat.messages.get: ${e.message}")
                e.printStackTrace()
                json.encodeToBytes(NatsErrorResponse(e.message ?: "Failed to get messages"))
            }
            nats.publish(msg.replyTo, responseBytes)
        }
    }.subscribe("chat.messages.get")
    // --- END NEW LISTENER ---


    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}