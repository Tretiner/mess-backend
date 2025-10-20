package org.mess.backend.chat

import io.ktor.server.config.*
import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mess.backend.chat.db.DatabaseConfig
import org.mess.backend.chat.db.initDatabase
import org.mess.backend.chat.models.*
import org.mess.backend.chat.services.ChatService
import org.mess.backend.chat.services.RemoteProfileService
import java.nio.charset.StandardCharsets
import java.util.*

// Глобальный JSON-парсер
val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = true
}

// CoroutineScope для обработки сообщений в отдельных потоках
val serviceScope = CoroutineScope(Dispatchers.IO)

fun main() {
    // 1. Загружаем конфигурацию
    val config = Config.load()
    println("Config loaded. Connecting to NATS at ${config.natsUrl}")

    // 2. Подключаемся к NATS
    val nats: Connection = Nats.connect(config.natsUrl)
    println("NATS connected.")

    // 3. Инициализируем сервисы
    // Этот сервис *сам* является NATS-клиентом к `user-service`
    val remoteProfileService = RemoteProfileService(nats, json)
    val chatService = ChatService(remoteProfileService)

    // 4. Подключаемся к БД
    initDatabase(config.db)
    println("Database connected. Awaiting requests...")

    // --- СЛУШАТЕЛЬ 1: `chat.message.incoming` (Pub/Sub) ---
    // Это самый сложный слушатель
    val msgDispatcher = nats.createDispatcher { msg ->
        // Запускаем в корутине, т.к. нам нужно сделать NATS-запрос к user-service
        serviceScope.launch {
            try {
                val incomingJson = String(msg.data, StandardCharsets.UTF_8)
                val incomingMsg = json.decodeFromString<NatsIncomingMessage>(incomingJson)

                // Вызываем бизнес-логику (которая вызовет NATS-запрос к user-service)
                val broadcastMessage = chatService.processIncomingMessage(incomingMsg)

                // Публикуем результат в 'broadcast'
                if (broadcastMessage != null) {
                    val broadcastJson = json.encodeToString(broadcastMessage)
                    nats.publish("chat.message.broadcast", broadcastJson.toByteArray(StandardCharsets.UTF_8))
                }

            } catch (e: Exception) {
                println("Error processing chat.message.incoming: ${e.message}")
            }
        }
    }
    msgDispatcher.subscribe("chat.message.incoming")

    // --- СЛУШАТЕЛЬ 2: `chat.create.group` (Request-Reply) ---
    val createGroupDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине, т.к. делаем N+1 NATS-запросы
            val responseJson = try {
                val request = json.decodeFromString<NatsChatCreateGroupRequest>(String(msg.data, StandardCharsets.UTF_8))
                val chat = chatService.createGroupChat(request)
                json.encodeToString(chat)
            } catch (e: Exception) {
                json.encodeToString(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
        }
    }
    createGroupDispatcher.subscribe("chat.create.group")

    // --- СЛУШАТЕЛЬ 3: `chat.create.dm` (Request-Reply) ---
    val createDmDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине, т.к. делаем N+1 NATS-запросы
            val responseJson = try {
                val request = json.decodeFromString<NatsChatCreateDmRequest>(String(msg.data, StandardCharsets.UTF_8))
                val chat = chatService.createDirectMessageChat(request)
                json.encodeToString(chat)
            } catch (e: Exception) {
                json.encodeToString(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
        }
    }
    createDmDispatcher.subscribe("chat.create.dm")

    // --- СЛУШАТЕЛЬ 4: `chat.get.mychats` (Request-Reply) ---
    val getChatsDispatcher = nats.createDispatcher { msg ->
        serviceScope.launch { // Запускаем в корутине, т.к. делаем N+1 NATS-запросы
            val responseJson = try {
                val request = json.decodeFromString<NatsGetMyChatsRequest>(String(msg.data, StandardCharsets.UTF_8))
                val response = chatService.getMyChats(request.userId)
                json.encodeToString(response)
            } catch (e: Exception) {
                json.encodeToString(NatsErrorResponse(e.message ?: "Error"))
            }
            nats.publish(msg.replyTo, responseJson.toByteArray(StandardCharsets.UTF_8))
        }
    }
    getChatsDispatcher.subscribe("chat.get.mychats")

    // Держим поток живым
    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutting down NATS connection...")
        nats.close()
    })
}