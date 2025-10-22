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
        serviceScope.launch {
            try {
                val incomingMsg = json.decodeFromBytes<NatsIncomingMessage>(msg.data)
                val broadcastMessage = chatService.processIncomingMessage(incomingMsg)
                if (broadcastMessage != null) {
                    nats.publish("chat.message.broadcast", json.encodeToBytes(broadcastMessage))
                }
            } catch (e: Exception) {
                println("Error processing chat.message.incoming: ${e.message}")
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


    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}