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
import io.nats.client.Dispatcher
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.core.decodeFromBytes
import org.mess.backend.core.encodeToBytes
import org.mess.backend.gateway.exceptions.ServiceException
import org.mess.backend.gateway.models.api.*
import org.mess.backend.gateway.models.*
import org.mess.backend.gateway.models.nats.*
import org.mess.backend.gateway.services.NatsClient
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets

val routingLog = LoggerFactory.getLogger("GatewayRouting")

suspend inline fun <T> ApplicationCall.handleNatsResult(
    result: Result<T>,
    successCode: HttpStatusCode = HttpStatusCode.OK,
    transform: (T) -> Any = { it as Any }
) {
    result.fold(
        onSuccess = { natsResponse ->
            try {
                val apiResponse = transform(natsResponse)
                respond(successCode, apiResponse)
            } catch (e: Exception) {
                routingLog.error("Error transforming NATS response: {}", e.message, e)
                respond(
                    HttpStatusCode.InternalServerError,
                    ErrorApiResponse("Internal gateway error during response transformation.")
                )
            }
        },
        onFailure = { exception ->
            if (exception is ServiceException) {
                routingLog.warn("NATS request failed: Status={}, Message={}", exception.statusCode, exception.message)
                respond(exception.statusCode, ErrorApiResponse(exception.message))
            } else {
                routingLog.error("Unexpected error handling NATS result: {}", exception.message, exception)
                respond(HttpStatusCode.InternalServerError, ErrorApiResponse("An unexpected gateway error occurred."))
            }
        }
    )
}

fun Application.configureRouting(nats: NatsClient, natsRaw: Connection, json: Json) {
    routing {
        route("/auth") {
            post("/register") {
                val request = call.receive<AuthApiRequest>()
                val natsAuthReq = NatsAuthRequest(request.username, request.password)
                val natsAuthResult = nats.request<NatsAuthRequest, NatsAuthResponse>("auth.register", natsAuthReq)

                natsAuthResult.onSuccess { natsAuthRes ->
                    val natsProfileReq = NatsProfileGetRequest(natsAuthRes.profile.id)
                    val natsProfileResult =
                        nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsProfileReq)

                    natsProfileResult.fold(
                        onSuccess = { natsProfileRes ->
                            val apiResponse = mapAuthAndProfileToApi(natsAuthRes, natsProfileRes)
                            call.respond(HttpStatusCode.Created, apiResponse)
                        },
                        onFailure = {
                            val apiResponse = mapAuthToApiWithStub(natsAuthRes)
                            call.respond(HttpStatusCode.Created, apiResponse)
                        }
                    )
                }.onFailure { exception ->
                    call.handleNatsResult(Result.failure<NatsAuthResponse>(exception))
                }
            }

            post("/login") {
                val request = call.receive<AuthApiRequest>()
                val natsAuthReq = NatsAuthRequest(request.username, request.password)
                val natsAuthResult = nats.request<NatsAuthRequest, NatsAuthResponse>("auth.login", natsAuthReq)

                natsAuthResult.onSuccess { natsAuthRes ->
                    val natsProfileReq = NatsProfileGetRequest(natsAuthRes.profile.id)
                    val natsProfileResult =
                        nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsProfileReq)

                    natsProfileResult.fold(
                        onSuccess = { natsProfileRes ->
                            val apiResponse = mapAuthAndProfileToApi(natsAuthRes, natsProfileRes)
                            call.respond(HttpStatusCode.OK, apiResponse)
                        },
                        onFailure = { profileError ->
                            call.handleNatsResult(Result.failure<NatsUserProfile>(profileError))
                        }
                    )
                }.onFailure { exception ->
                    call.handleNatsResult(Result.failure<NatsAuthResponse>(exception))
                }
            }
        }

        authenticate("auth-jwt") {
            route("/users") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val natsReq = NatsProfileGetRequest(userId)
                    val result = nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsReq)
                    call.handleNatsResult(result) { it.toApi() }
                }
                put("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdateProfileApiRequest>()
                    val natsReq = NatsProfileUpdateRequest(
                        userId,
                        request.newUsername,
                        request.newAvatarUrl,
                        request.newEmail,
                        request.newFullName
                    )
                    val result = nats.request<NatsProfileUpdateRequest, NatsUserProfile>("user.profile.update", natsReq)
                    call.handleNatsResult(result) { it.toApi() }
                }
                get("/search") {
                    val query = call.request.queryParameters["q"] ?: run {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorApiResponse("Query parameter 'q' is required.")
                        )
                    }
                    val natsReq = NatsSearchRequest(query)
                    val result = nats.request<NatsSearchRequest, NatsSearchResponse>("user.search", natsReq)
                    call.handleNatsResult(result) { it.toApi() }
                }
            }

            route("/chats") {
                // GET /chats
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val natsReq = NatsGetMyChatsRequest(userId)
                    val result =
                        nats.request<NatsGetMyChatsRequest, NatsGetMyChatsResponse>("chat.get.mychats", natsReq)
                    call.handleNatsResult(result) { it.toApi(userId) }
                }

                // POST /chats/group
                post("/group") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val creatorId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<CreateGroupChatApiRequest>() // Принимает avatarUrl
                    val natsReq = NatsChatCreateGroupRequest(
                        creatorId,
                        request.name,
                        request.memberIds,
                        request.avatarUrl // Передает avatarUrl
                    )
                    val result = nats.request<NatsChatCreateGroupRequest, NatsChat>("chat.create.group", natsReq)
                    call.handleNatsResult(result, HttpStatusCode.Created) { it.toApi(creatorId) }
                }

                // POST /chats/dm/{otherUserId}
                post("/dm/{otherUserId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val myId = principal.payload.getClaim("userId").asString()
                    val otherUserId = call.parameters["otherUserId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest, ErrorApiResponse("Missing otherUserId parameter")
                    )
                    val natsReq = NatsChatCreateDmRequest(userId1 = myId, userId2 = otherUserId)
                    val result = nats.request<NatsChatCreateDmRequest, NatsChat>("chat.create.dm", natsReq)
                    call.handleNatsResult(result) { it.toApi(myId) }
                }

                // --- НОВЫЕ ЭНДПОИНТЫ ---

                route("/{chatId}") {
                    // GET /chats/{chatId}
                    get {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = principal.payload.getClaim("userId").asString()
                        val chatId = call.parameters["chatId"] ?: return@get call.respond(
                            HttpStatusCode.BadRequest, ErrorApiResponse("Missing chatId parameter")
                        )
                        val natsReq = NatsGetChatDetailsRequest(chatId, userId)
                        val result = nats.request<NatsGetChatDetailsRequest, NatsChat>("chat.get.details", natsReq)
                        call.handleNatsResult(result) { it.toApi(userId) }
                    }

                    // PUT /chats/{chatId}
                    put {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = principal.payload.getClaim("userId").asString()
                        val chatId = call.parameters["chatId"] ?: return@put call.respond(
                            HttpStatusCode.BadRequest, ErrorApiResponse("Missing chatId parameter")
                        )
                        val request = call.receive<UpdateChatApiRequest>()
                        val natsReq = NatsUpdateChatRequest(
                            chatId = chatId,
                            requestedByUserId = userId,
                            newName = request.newName,
                            newAvatarUrl = request.newAvatarUrl
                        )
                        val result = nats.request<NatsUpdateChatRequest, NatsChat>("chat.update.details", natsReq)
                        call.handleNatsResult(result) { it.toApi(userId) }
                    }

                    get("/messages") {
                        val principal = call.principal<JWTPrincipal>()!!
                        val userId = principal.payload.getClaim("userId").asString()
                        val chatId = call.parameters["chatId"] ?: return@get call.respond(
                            HttpStatusCode.BadRequest, ErrorApiResponse("Missing chatId parameter")
                        )
                        // TODO: Get pagination params from query ?limit=X&before=timestamp
                        val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                        val beforeTimestampStr = call.request.queryParameters["before"]
                        val beforeInstant = try {
                            beforeTimestampStr?.let { Instant.parse(it) }
                        } catch (e: Exception) {
                            null
                        }

                        routingLog.info(
                            "<<< GW: User {} requesting messages for chat {} (limit: {})",
                            userId,
                            chatId,
                            limit
                        )

                        val natsReq = NatsMessagesGetRequest(
                            chatId = chatId,
                            userId = userId, // Send requesting user's ID for membership check
                            limit = limit,
                            beforeInstant = beforeInstant
                        )
                        val result =
                            nats.request<NatsMessagesGetRequest, NatsMessagesGetResponse>("chat.messages.get", natsReq)
                        call.handleNatsResult(result) { it.toApi() } // Use NatsMessagesGetResponse.toApi()
                    }
                }

                // DELETE /chats/{chatId}/members/{userIdToRemove}
                delete("/{chatId}/members/{userIdToRemove}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val requestedByUserId = principal.payload.getClaim("userId").asString()
                    val chatId = call.parameters["chatId"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest, ErrorApiResponse("Missing chatId parameter")
                    )
                    val userIdToRemove = call.parameters["userIdToRemove"] ?: return@delete call.respond(
                        HttpStatusCode.BadRequest, ErrorApiResponse("Missing userIdToRemove parameter")
                    )

                    val natsReq = NatsRemoveUserRequest(
                        chatId = chatId,
                        requestedByUserId = requestedByUserId,
                        userIdToRemove = userIdToRemove
                    )
                    // Ожидаем пустой ответ или NatsErrorResponse
                    val result = nats.request<NatsRemoveUserRequest, Unit>("chat.remove.user", natsReq)
                    call.handleNatsResult(result, HttpStatusCode.NoContent) { } // Успех = 204 No Content
                }
            }

            // --- WebSocket для чата ---
            webSocket("/chat") {
                // Получаем principal из CallContext
                val principal = call.principal<JWTPrincipal>() ?: run {
                    routingLog.error("!!! GW WS: Principal missing within authenticated WebSocket route!")
                    close(CloseReason(CloseReason.Codes.PROTOCOL_ERROR, "Authentication context error"))
                    return@webSocket
                }
                val userId = principal.payload.getClaim("userId").asString()
                routingLog.info("<<< GW WS: Connection established for user: {}", userId)

                var dispatcher: Dispatcher? = null // Переменная для NATS диспетчера

                try {
                    // Подписываемся на личную NATS-тему для broadcast-сообщений
                    val broadcastTopic = "chat.broadcast.$userId"
                    dispatcher = natsRaw.createDispatcher { msg ->
                        // Запускаем корутину для обработки каждого NATS сообщения
                        launch {
                            routingLog.debug("<<< GW WS: Received NATS message on {} for user {}", msg.subject, userId)
                            try {
                                // Декодируем NATS сообщение
                                val natsMsg = json.decodeFromBytes<NatsBroadcastMessage>(msg.data)
                                // Маппим в API модель
                                val apiMsg = natsMsg.toApi()
                                // Сериализуем API модель
                                val apiJson = json.encodeToBytes(apiMsg)
                                routingLog.debug(
                                    "<<< GW WS: Sending broadcast (MsgID: {}) to user {}",
                                    natsMsg.messageId,
                                    userId
                                )
                                // Отправляем клиенту через WebSocket
                                outgoing.send(Frame.Text(String(apiJson, StandardCharsets.UTF_8)))
                            } catch (e: Exception) {
                                routingLog.error(
                                    "!!! GW WS: Error processing/sending broadcast to user {}: {}",
                                    userId,
                                    e.message,
                                    e
                                )
                                // Можно рассмотреть закрытие соединения при ошибках отправки
                                // close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, "Broadcast processing failed"))
                            }
                        }
                    }.apply { subscribe(broadcastTopic) } // Подписываемся на тему
                    routingLog.info("<<< GW WS: Subscribed user {} to NATS topic: {}", userId, broadcastTopic)

                    // Обрабатываем входящие сообщения от клиента, используя Flow
                    routingLog.info("<<< GW WS: Starting to listen for incoming frames from user {}", userId)

                    incoming.receiveAsFlow() // Получаем поток входящих фреймов
                        .mapNotNull { it as? Frame.Text } // Фильтруем только текстовые фреймы
                        .collect { frame -> // Обрабатываем каждый текстовый фрейм
                            routingLog.info(
                                "<<< GW WS: Received TEXT frame from user {}",
                                userId
                            ) // Лог получения фрейма
                            val text = frame.readText() // Читаем текст
                            routingLog.debug(
                                "<<< GW WS: Received text content: {}",
                                text.take(100)
                            ) // Лог контента (частично)

                            try {
                                // Пытаемся распарсить как сообщение чата
                                val clientMsg = json.decodeFromString<ChatMessageApiRequest>(text)
                                // Создаем NATS сообщение (добавляем userId из токена)
                                val natsMsg =
                                    NatsIncomingMessage(userId, clientMsg.chatId, clientMsg.type, clientMsg.content)
                                routingLog.info(
                                    "<<< GW WS: Publishing message from user {} to NATS 'chat.message.incoming'",
                                    userId
                                ) // Лог публикации
                                // Публикуем в общую NATS тему для обработки chat-service
                                natsRaw.publish("chat.message.incoming", json.encodeToBytes(natsMsg))
                            } catch (e: SerializationException) {
                                // Ошибка парсинга JSON
                                routingLog.warn(
                                    "!!! GW WS: Invalid JSON format from user {}: {}. Raw text: {}",
                                    userId,
                                    e.message,
                                    text.take(100)
                                )
                                // Отправляем клиенту сообщение об ошибке формата
                                val errorApi = ErrorApiResponse("Invalid message format sent.")
                                try {
                                    outgoing.send(Frame.Text(String(json.encodeToBytes(errorApi))))
                                } catch (sendError: Exception) {
                                    routingLog.error(
                                        "!!! GW WS: Failed to send format error back to user {}: {}",
                                        userId,
                                        sendError.message
                                    )
                                }
                            } catch (e: Exception) {
                                // Другие непредвиденные ошибки
                                routingLog.error(
                                    "!!! GW WS: Unexpected error processing incoming text from user {}: {}",
                                    userId,
                                    e.message,
                                    e
                                )
                            }
                        }

                } catch (e: Exception) {
                    // Обработка ошибок сессии WebSocket
                    routingLog.error("!!! GW WS: Error in WebSocket session for user {}: {}", userId, e.message, e)
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.localizedMessage ?: "Session error"))
                } finally {
                    // Блок finally: выполняется при закрытии соединения (нормальном или из-за ошибки)
                    routingLog.info(
                        "<<< GW WS: WebSocket session ending for user: {}. Closing NATS dispatcher.",
                        userId
                    )
                    // Отписываемся от NATS темы
                    dispatcher?.let {
                        try {
                            natsRaw.closeDispatcher(it)
                            routingLog.debug("<<< GW WS: NATS dispatcher closed for user {}", userId)
                        } catch (e: Exception) {
                            routingLog.error(
                                "!!! GW WS: Error closing NATS dispatcher for user {}: {}",
                                userId,
                                e.message
                            )
                        }
                    }
                }
            } // Конец блока webSocket
        }
    }
}