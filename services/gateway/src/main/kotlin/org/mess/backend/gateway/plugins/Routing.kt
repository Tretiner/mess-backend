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
import io.nats.client.Dispatcher // Импорт для NATS Dispatcher
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import org.mess.backend.gateway.exceptions.ServiceException // Наше кастомное исключение
import org.mess.backend.gateway.json // Глобальный Json парсер
import org.mess.backend.gateway.log // Глобальный логгер
import org.mess.backend.gateway.models.api.* // Модели для API (REST/WebSocket)
import org.mess.backend.gateway.models.nats.* // Модели для NATS
import org.mess.backend.gateway.services.NatsClient // Хелпер для NATS Request-Reply
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

// Функция расширения для Application, настраивающая все маршруты
fun Application.configureRouting(nats: NatsClient, natsRaw: Connection) {
    val log = LoggerFactory.getLogger("Routing")

    routing {
        // --- 1. Публичные роуты (не требуют JWT) ---
        route("/auth") {
            post("/register") {
                val request = call.receive<AuthApiRequest>() // Получаем JSON из тела запроса
                log.info("Received registration request for user: {}", request.username)
                val natsAuthReq = NatsAuthRequest(request.username, request.password)

                // 1. Запрос к auth-service для регистрации (токен + ID/username заглушка)
                val natsAuthRes = nats.request<NatsAuthRequest, NatsAuthResponse>("auth.register", natsAuthReq)

                // 2. Запрос к user-service для получения ПОЛНОГО профиля по ID
                val natsProfileReq = NatsProfileGetRequest(natsAuthRes.profile.id)
                val natsProfileRes = try {
                    nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsProfileReq)
                } catch (e: ServiceException) {
                    // Если user-service еще не успел обработать событие user.created (маловероятно, но возможно)
                    // или произошла другая ошибка при запросе профиля, возвращаем базовый профиль из заглушки.
                    log.warn("User profile request failed after registration for ID {}. Returning stub profile. Error: {}", natsAuthRes.profile.id, e.message)
                    // Создаем полный NatsUserProfile из данных заглушки
                    NatsUserProfile(
                        id = natsAuthRes.profile.id,
                        nickname = natsAuthRes.profile.username, // Используем username как nickname
                        avatarUrl = null,
                        email = null,
                        fullName = null
                    )
                }

                log.info("Registration successful for user: {}", request.username)
                // Собираем финальный ответ для API с полным профилем
                val apiResponse = AuthApiResponse(natsAuthRes.token, natsProfileRes.toApi())
                call.respond(HttpStatusCode.Created, apiResponse)
            }
            post("/login") {
                val request = call.receive<AuthApiRequest>()
                log.info("Received login request for user: {}", request.username)
                val natsAuthReq = NatsAuthRequest(request.username, request.password)

                // 1. Запрос к auth-service для логина (токен + ID/username заглушка)
                val natsAuthRes = nats.request<NatsAuthRequest, NatsAuthResponse>("auth.login", natsAuthReq)

                log.info("Login successful for user: {}", request.username)
                // Собираем финальный ответ для API с полным профилем
                val apiResponse = AuthApiResponse(natsAuthRes.token, Profile)
                call.respond(HttpStatusCode.OK, apiResponse)
            }
        }

        // --- 2. Защищенные роуты (требуют валидного JWT) ---
        authenticate("auth-jwt") { // Ktor проверит токен перед выполнением кода внутри блока

            // --- Пользователи ---
            route("/users") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    log.info("Getting profile for user: {}", userId)
                    val natsReq = NatsProfileGetRequest(userId)
                    val natsRes = nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsReq)
                    call.respond(natsRes.toApi()) // Маппер toApi() включает новые поля
                }

                put("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdateProfileApiRequest>() // Получаем JSON с новыми полями
                    log.info("Updating profile for user: {}", userId)

                    val natsReq = NatsProfileUpdateRequest( // Создаем NATS-запрос с новыми полями
                        userId = userId,
                        newNickname = request.newNickname,
                        newAvatarUrl = request.newAvatarUrl,
                        newEmail = request.newEmail,
                        newFullName = request.newFullName
                    )
                    val natsRes = nats.request<NatsProfileUpdateRequest, NatsUserProfile>("user.profile.update", natsReq)
                    log.info("Profile updated successfully for user: {}", userId)
                    call.respond(natsRes.toApi()) // Возвращаем обновленный профиль с новыми полями
                }

                get("/search") {
                    val query = call.request.queryParameters["q"]
                    if (query.isNullOrBlank()) {
                        log.warn("Search request received without 'q' parameter")
                        return@get call.respond(HttpStatusCode.BadRequest, ErrorApiResponse("Query parameter 'q' is required."))
                    }
                    log.info("Searching users with query: {}", query)
                    val natsReq = NatsSearchRequest(query)
                    val natsRes = nats.request<NatsSearchRequest, NatsSearchResponse>("user.search", natsReq)
                    call.respond(natsRes.toApi()) // Маппер toApi() для NatsSearchResponse использует маппер для NatsUserProfile
                }
            }

            // --- Чаты ---
            route("/chats") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    log.info("Getting chats for user: {}", userId)
                    val natsReq = NatsGetMyChatsRequest(userId)
                    val natsRes = nats.request<NatsGetMyChatsRequest, NatsGetMyChatsResponse>("chat.get.mychats", natsReq)
                    // Передаем userId для правильного маппинга имен DM-чатов и полных профилей
                    call.respond(natsRes.toApi(userId))
                }

                post("/group") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val creatorId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<CreateGroupChatApiRequest>()
                    log.info("User {} creating group chat '{}' with members: {}", creatorId, request.name, request.memberIds)

                    val natsReq = NatsChatCreateGroupRequest(
                        creatorId = creatorId,
                        name = request.name,
                        memberIds = request.memberIds
                    )
                    val natsRes = nats.request<NatsChatCreateGroupRequest, NatsChat>("chat.create.group", natsReq)
                    log.info("Group chat created successfully with id: {}", natsRes.id)
                    call.respond(HttpStatusCode.Created, natsRes.toApi(creatorId))
                }

                post("/dm/{otherUserId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val myId = principal.payload.getClaim("userId").asString()
                    val otherUserId = call.parameters["otherUserId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorApiResponse("Missing otherUserId parameter"))
                    log.info("User {} creating DM chat with user: {}", myId, otherUserId)

                    val natsReq = NatsChatCreateDmRequest(userId1 = myId, userId2 = otherUserId)
                    val natsRes = nats.request<NatsChatCreateDmRequest, NatsChat>("chat.create.dm", natsReq)
                    log.info("DM chat created/retrieved successfully with id: {}", natsRes.id)
                    call.respond(HttpStatusCode.OK, natsRes.toApi(myId))
                }

                // Добавление пользователя в чат
                post("/{chatId}/members/{userId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val addedByUserId = principal.payload.getClaim("userId").asString()
                    val chatId = call.parameters["chatId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorApiResponse("Missing chatId parameter"))
                    val userIdToAdd = call.parameters["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest, ErrorApiResponse("Missing userId parameter"))
                    log.info("User {} adding user {} to chat {}", addedByUserId, userIdToAdd, chatId)

                    // Оборачиваем в try-catch не нужно, т.к. StatusPages перехватит ServiceException
                    val natsReq = NatsAddUserToChatRequest(
                        addedByUserId = addedByUserId,
                        chatId = chatId,
                        userIdToAdd = userIdToAdd
                    )
                    // Делаем запрос к chat-service на добавление
                    val natsRes = nats.request<NatsAddUserToChatRequest, NatsChat>("chat.member.add", natsReq)
                    log.info("User {} added successfully to chat {}", userIdToAdd, chatId)
                    call.respond(HttpStatusCode.OK, natsRes.toApi(addedByUserId))
                    // Если nats.request бросит ServiceException, его перехватит StatusPages
                }
            }

            // --- WebSocket для чата ---
            webSocket("/chat") { // Путь к WebSocket эндпоинту
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                log.info("WebSocket connection established for user: {}", userId)

                // Объявляем dispatcher заранее, чтобы он был доступен в finally
                var dispatcher: Dispatcher? = null // Используем var и null

                try {
                    // 1. ПОДПИСКА (NATS -> WebSocket)
                    val broadcastTopic = "chat.broadcast.$userId"
                    dispatcher = natsRaw.createDispatcher { msg -> // Присваиваем dispatcher здесь
                        // Запускаем отправку в корутине сессии WebSocket
                        launch { // 'launch' неявно использует CoroutineScope этой WebSocket-сессии
                            try {
                                val msgJson = String(msg.data, StandardCharsets.UTF_8)
                                // Просто пересылаем JSON как текст в WebSocket
                                outgoing.send(Frame.Text(msgJson))
                                log.debug("Sent broadcast message to user {} via WebSocket", userId)
                            } catch (e: Exception) {
                                // Ловим ошибки отправки, если клиент внезапно отключился или другая проблема
                                log.error("Error sending broadcast to WebSocket for user {}: {}. Closing WebSocket.", userId, e.message)
                                // Не закрываем dispatcher здесь, закроется в finally
                                close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, "Failed to send message: ${e.message}")) // Закрываем WebSocket
                            }
                        }
                    }.apply { subscribe(broadcastTopic) } // Подписываемся сразу после создания

                    log.info("Subscribed to NATS topic: {}", broadcastTopic)

                    // 2. ПУБЛИКАЦИЯ (WebSocket -> NATS)
                    // Слушаем входящие фреймы от клиента
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            log.debug("Received message from user {} via WebSocket: {}", userId, text)
                            try {
                                // Парсим JSON, пришедший от клиента
                                val clientMsg = json.decodeFromString<ChatMessageApiRequest>(text)
                                // Добавляем userId и формируем сообщение для NATS
                                val natsMsg = NatsIncomingMessage(
                                    userId = userId,
                                    chatId = clientMsg.chatId,
                                    type = clientMsg.type,
                                    content = clientMsg.content
                                )
                                val natsJson = json.encodeToString(NatsIncomingMessage.serializer(), natsMsg)
                                // Публикуем в общую тему для обработки chat-service
                                natsRaw.publish("chat.message.incoming", natsJson.toByteArray(StandardCharsets.UTF_8))
                                log.debug("Published incoming message to NATS topic 'chat.message.incoming'")
                            } catch (e: Exception) {
                                log.error("Error processing incoming WebSocket message from user {}: {}", userId, e.message)
                                // Отправляем сообщение об ошибке обратно клиенту
                                try {
                                    outgoing.send(Frame.Text(json.encodeToString(ErrorApiResponse("Invalid message format: ${e.message}"))))
                                } catch (sendError: Exception) {
                                    log.error("Failed to send error back to user {}: {}", userId, sendError.message)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    log.error("Error in WebSocket session for user {}: {}", userId, e.message, e)
                    // Закрываем соединение с кодом ошибки
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.localizedMessage ?: "WebSocket error"))
                } finally {
                    // 3. ОЧИСТКА - выполняется всегда при выходе из try/catch
                    log.info("WebSocket connection closed for user: {}. Closing NATS dispatcher.", userId)
                    // Корректно закрываем NATS dispatcher здесь, если он был создан
                    dispatcher?.let { natsRaw.closeDispatcher(it) }
                }
            }
        }
    }
}