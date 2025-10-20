// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/Routing.kt
package org.mess.backend.gateway.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import io.nats.client.Connection
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.mess.backend.gateway.exceptions.ServiceException // Наше кастомное исключение
import org.mess.backend.gateway.json // Глобальный Json парсер
import org.mess.backend.gateway.log // Глобальный логгер
import org.mess.backend.gateway.models.api.* // Модели для API (REST/WebSocket)
import org.mess.backend.gateway.models.nats.* // Модели для NATS
import org.mess.backend.gateway.services.NatsClient // Хелпер для NATS Request-Reply
import java.nio.charset.StandardCharsets

// Функция расширения для Application, настраивающая все маршруты
fun Application.configureRouting(nats: NatsClient, natsRaw: Connection) {
    routing {
        // --- 1. Публичные роуты (не требуют JWT) ---
        route("/auth") {
            post("/register") {
                val request = call.receive<AuthApiRequest>() // Получаем JSON из тела запроса
                this@configureRouting.log.info("Received registration request for user: {}", request.username)
                val natsReq = NatsAuthRequest(request.username, request.password)
                // Делаем запрос к auth-сервису через NatsClient
                val natsRes = nats.request<NatsAuthRequest, NatsAuthResponse>("auth.register", natsReq)
                this@configureRouting.log.info("Registration successful for user: {}", request.username)
                // Отправляем ответ клиенту
                call.respond(HttpStatusCode.Created, natsRes.toApi())
            }
            post("/login") {
                val request = call.receive<AuthApiRequest>()
                this@configureRouting.log.info("Received login request for user: {}", request.username)
                val natsReq = NatsAuthRequest(request.username, request.password)
                val natsRes = nats.request<NatsAuthRequest, NatsAuthResponse>("auth.login", natsReq)
                this@configureRouting.log.info("Login successful for user: {}", request.username)
                call.respond(HttpStatusCode.OK, natsRes.toApi())
            }
        }

        // --- 2. Защищенные роуты (требуют валидного JWT) ---
        authenticate("auth-jwt") { // Ktor проверит токен перед выполнением кода внутри блока

            // --- Пользователи ---
            route("/users") {
                get("/me") {
                    // Получаем данные пользователя из JWT
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    this@configureRouting.log.info("Getting profile for user: {}", userId)
                    val natsReq = NatsProfileGetRequest(userId)
                    // Запрос к user-сервису
                    val natsRes = nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsReq)
                    call.respond(natsRes.toApi())
                }

                put("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    // Получаем данные для обновления из тела запроса
                    val request = call.receive<UpdateProfileApiRequest>()
                    this@configureRouting.log.info("Updating profile for user: {}", userId)

                    val natsReq = NatsProfileUpdateRequest(
                        userId = userId,
                        newNickname = request.newNickname, // Ktor обработает null, если поле отсутствует
                        newAvatarUrl = request.newAvatarUrl
                    )
                    // Запрос к user-сервису
                    val natsRes = nats.request<NatsProfileUpdateRequest, NatsUserProfile>("user.profile.update", natsReq)
                    this@configureRouting.log.info("Profile updated successfully for user: {}", userId)
                    call.respond(natsRes.toApi())
                }

                get("/search") {
                    val query = call.request.queryParameters["q"]
                    if (query.isNullOrBlank()) {
                        this@configureRouting.log.warn("Search request received without 'q' parameter")
                        return@get call.respond(HttpStatusCode.BadRequest, ErrorApiResponse("Query parameter 'q' is required."))
                    }
                    this@configureRouting.log.info("Searching users with query: {}", query)
                    val natsReq = NatsSearchRequest(query)
                    val natsRes = nats.request<NatsSearchRequest, NatsSearchResponse>("user.search", natsReq)
                    call.respond(natsRes.toApi())
                }
            }

            // --- Чаты ---
            route("/chats") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    this@configureRouting.log.info("Getting chats for user: {}", userId)
                    val natsReq = NatsGetMyChatsRequest(userId)
                    val natsRes = nats.request<NatsGetMyChatsRequest, NatsGetMyChatsResponse>("chat.get.mychats", natsReq)
                    // Передаем userId для правильного маппинга имен DM-чатов
                    call.respond(natsRes.toApi(userId))
                }

                post("/group") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val creatorId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<CreateGroupChatApiRequest>()
                    this@configureRouting.log.info("User {} creating group chat '{}' with members: {}", creatorId, request.name, request.memberIds)

                    val natsReq = NatsChatCreateGroupRequest(
                        creatorId = creatorId,
                        name = request.name,
                        memberIds = request.memberIds
                    )
                    val natsRes = nats.request<NatsChatCreateGroupRequest, NatsChat>("chat.create.group", natsReq)
                    this@configureRouting.log.info("Group chat created successfully with id: {}", natsRes.id)
                    call.respond(HttpStatusCode.Created, natsRes.toApi(creatorId))
                }

                post("/dm/{otherUserId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val myId = principal.payload.getClaim("userId").asString()
                    val otherUserId = call.parameters["otherUserId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    this@configureRouting.log.info("User {} creating DM chat with user: {}", myId, otherUserId)

                    val natsReq = NatsChatCreateDmRequest(userId1 = myId, userId2 = otherUserId)
                    val natsRes = nats.request<NatsChatCreateDmRequest, NatsChat>("chat.create.dm", natsReq)
                    this@configureRouting.log.info("DM chat created/retrieved successfully with id: {}", natsRes.id)
                    call.respond(HttpStatusCode.OK, natsRes.toApi(myId))
                }

                // Добавление пользователя в чат
                post("/{chatId}/members/{userId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val addedByUserId = principal.payload.getClaim("userId").asString()
                    val chatId = call.parameters["chatId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userIdToAdd = call.parameters["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    this@configureRouting.log.info("User {} adding user {} to chat {}", addedByUserId, userIdToAdd, chatId)

                    // Оборачиваем в try-catch на случай, если NatsClient бросит ServiceException
                    // StatusPages обработает исключение и отправит клиенту корректный ответ
                    val natsReq = NatsAddUserToChatRequest(
                        addedByUserId = addedByUserId,
                        chatId = chatId,
                        userIdToAdd = userIdToAdd
                    )
                    // Делаем запрос к chat-service на добавление
                    val natsRes = nats.request<NatsAddUserToChatRequest, NatsChat>("chat.member.add", natsReq)
                    this@configureRouting.log.info("User {} added successfully to chat {}", userIdToAdd, chatId)
                    call.respond(HttpStatusCode.OK, natsRes.toApi(addedByUserId))
                    // Если nats.request бросит ServiceException, его перехватит StatusPages
                }
            }

            // --- WebSocket для чата ---
            webSocket("/chat") { // 'this' здесь - это DefaultWebSocketServerSession
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                this@configureRouting.log.info("WebSocket connection established for user: {}", userId)

                // 1. ПОДПИСКА (NATS -> WebSocket)
                val broadcastTopic = "chat.broadcast.$userId"
                val dispatcher = natsRaw.createDispatcher { msg ->
                    // --- ИСПРАВЛЕНИЕ ---
                    // Запускаем отправку в корутине сессии WebSocket
                    launch { // 'launch' неявно использует CoroutineScope этой WebSocket-сессии
                        try {
                            val msgJson = String(msg.data, StandardCharsets.UTF_8)
                            outgoing.send(Frame.Text(msgJson))
                            this@configureRouting.log.debug("Sent broadcast message to user {} via WebSocket", userId)
                        } catch (e: Exception) {
                            this@configureRouting.log.error("Error sending broadcast to WebSocket for user {}: {}. Closing NATS dispatcher and WS.", userId, e.message)

//                            natsRaw.closeDispatcher(this) // Отписываемся от NATS
                            close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "NATS send failed")) // Закрываем WS
                        }
                    }
                    // --- КОНЕЦ ИСПРАВЛЕНИЯ ---
                }
                dispatcher.subscribe(broadcastTopic)
                this@configureRouting.log.info("Subscribed to NATS topic: {}", broadcastTopic)

                try {
                    // 2. ПУБЛИКАЦИЯ (WebSocket -> NATS)
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            this@configureRouting.log.debug("Received message from user {} via WebSocket: {}", userId, text)
                            try {
                                val clientMsg = json.decodeFromString<ChatMessageApiRequest>(text)
                                val natsMsg = NatsIncomingMessage(
                                    userId = userId,
                                    chatId = clientMsg.chatId,
                                    type = clientMsg.type,
                                    content = clientMsg.content
                                )
                                val natsJson = json.encodeToString(NatsIncomingMessage.serializer(), natsMsg)
                                natsRaw.publish("chat.message.incoming", natsJson.toByteArray(StandardCharsets.UTF_8))
                                this@configureRouting.log.debug("Published incoming message to NATS topic 'chat.message.incoming'")
                            } catch (e: Exception) {
                                this@configureRouting.log.error("Error processing incoming WebSocket message from user {}: {}", userId, e.message)
                                try {
                                    outgoing.send(Frame.Text(json.encodeToString(ErrorApiResponse("Invalid message format: ${e.message}"))))
                                } catch (sendError: Exception) {
                                    this@configureRouting.log.error("Failed to send error back to user {}: {}", userId, sendError.message)
                                }
                            }
                        }
                    }
                } finally {
                    // 3. ОЧИСТКА
                    this@configureRouting.log.info("WebSocket connection closed for user: {}", userId)
                    natsRaw.closeDispatcher(dispatcher) // Отписываемся от NATS
                }
            }
        }
    }
}