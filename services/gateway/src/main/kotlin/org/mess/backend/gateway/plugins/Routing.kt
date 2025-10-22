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
import io.nats.client.Dispatcher
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.mess.backend.core.NatsErrorResponse // Используем общую модель ошибки из core
import org.mess.backend.core.encodeToBytes // Используем общую функцию расширения из core
import org.mess.backend.gateway.exceptions.ServiceException // Наше кастомное исключение
import org.mess.backend.gateway.models.api.* // Модели для API (REST/WebSocket)
import org.mess.backend.gateway.models.*
import org.mess.backend.gateway.models.nats.* // Модели для NATS
import org.mess.backend.gateway.services.NatsClient // Хелпер для NATS Request-Reply
import org.slf4j.LoggerFactory // Используем SLF4J для логирования
import java.nio.charset.StandardCharsets

// Получаем логгер специально для роутинга
val routingLog = LoggerFactory.getLogger("GatewayRouting")

suspend inline fun <T> ApplicationCall.handleNatsResult(
    result: Result<T>, // Результат вызова nats.request
    successCode: HttpStatusCode = HttpStatusCode.OK, // Код при успехе (по умолчанию 200)
    // Функция для преобразования NATS-ответа (T) в API-ответ (Any)
    transform: (T) -> Any = { it as Any } // По умолчанию возвращает как есть
) {
    result.fold(
        onSuccess = { natsResponse ->
            // Успех: преобразуем ответ и отправляем клиенту
            try {
                val apiResponse = transform(natsResponse)
                respond(successCode, apiResponse)
            } catch (e: Exception) {
                // Ошибка при преобразовании ответа (например, в маппере)
                routingLog.error("Error transforming NATS response: {}", e.message, e)
                respond(
                    HttpStatusCode.InternalServerError,
                    ErrorApiResponse("Internal gateway error during response transformation.")
                )
            }
        },
        onFailure = { exception ->
            // Неудача: извлекаем статус и сообщение из ServiceException
            if (exception is ServiceException) {
                routingLog.warn("NATS request failed: Status={}, Message={}", exception.statusCode, exception.message)
                respond(exception.statusCode, ErrorApiResponse(exception.message))
            } else {
                // Обработка других, не ожидаемых исключений (если NatsClient их пробросит)
                routingLog.error("Unexpected error handling NATS result: {}", exception.message, exception)
                respond(HttpStatusCode.InternalServerError, ErrorApiResponse("An unexpected gateway error occurred."))
            }
        }
    )
}

// Функция расширения для Application, настраивающая все маршруты
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
                            // Маппим без refreshToken
                            val apiResponse = mapAuthAndProfileToApi(natsAuthRes, natsProfileRes)
                            call.respond(HttpStatusCode.Created, apiResponse)
                        },
                        onFailure = {
                            // Возвращаем успех регистрации, но с неполным профилем (stub)
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

            // --- УДАЛЕН ЭНДПОИНТ /auth/refresh ---
        }

        authenticate("auth-jwt") {
            route("/users") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    routingLog.info("Getting profile for user: {}", userId)
                    val natsReq = NatsProfileGetRequest(userId)
                    val result = nats.request<NatsProfileGetRequest, NatsUserProfile>("user.profile.get", natsReq)
                    call.handleNatsResult(result) { it.toApi() } // NatsUserProfile.toApi()
                }
                put("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UpdateProfileApiRequest>() // Uses newUsername
                    routingLog.info("Updating profile for user: {}", userId)
                    // Pass newUsername to NatsProfileUpdateRequest
                    val natsReq = NatsProfileUpdateRequest(
                        userId,
                        request.newUsername,
                        request.newAvatarUrl,
                        request.newEmail,
                        request.newFullName
                    )
                    val result = nats.request<NatsProfileUpdateRequest, NatsUserProfile>("user.profile.update", natsReq)
                    routingLog.info("Profile update request sent for user: {}", userId)
                    call.handleNatsResult(result) { it.toApi() } // NatsUserProfile.toApi()
                }
                get("/search") {
                    val query = call.request.queryParameters["q"] ?: run {
                        return@get call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorApiResponse("Query parameter 'q' is required.")
                        )
                    }
                    routingLog.info("Searching users with query: {}", query)
                    val natsReq = NatsSearchRequest(query)
                    val result = nats.request<NatsSearchRequest, NatsSearchResponse>("user.search", natsReq)
                    call.handleNatsResult(result) { it.toApi() } // NatsSearchResponse.toApi()
                }
            }

            route("/chats") {
                get {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    routingLog.info("Getting chats for user: {}", userId)
                    val natsReq = NatsGetMyChatsRequest(userId)
                    val result =
                        nats.request<NatsGetMyChatsRequest, NatsGetMyChatsResponse>("chat.get.mychats", natsReq)
                    call.handleNatsResult(result) { it.toApi(userId) } // NatsGetMyChatsResponse.toApi()
                }
                post("/group") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val creatorId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<CreateGroupChatApiRequest>()
                    routingLog.info("User {} creating group chat '{}'", creatorId, request.name)
                    val natsReq = NatsChatCreateGroupRequest(creatorId, request.name, request.memberIds)
                    val result = nats.request<NatsChatCreateGroupRequest, NatsChat>("chat.create.group", natsReq)
                    call.handleNatsResult(result, HttpStatusCode.Created) { it.toApi(creatorId) } // NatsChat.toApi()
                }
                post("/dm/{otherUserId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val myId = principal.payload.getClaim("userId").asString()
                    val otherUserId = call.parameters["otherUserId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorApiResponse("Missing otherUserId parameter")
                    )
                    routingLog.info("User {} creating DM chat with user: {}", myId, otherUserId)
                    val natsReq = NatsChatCreateDmRequest(userId1 = myId, userId2 = otherUserId)
                    val result = nats.request<NatsChatCreateDmRequest, NatsChat>("chat.create.dm", natsReq)
                    call.handleNatsResult(result) { it.toApi(myId) } // NatsChat.toApi()
                }
                post("/{chatId}/members/{userId}") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val addedByUserId = principal.payload.getClaim("userId").asString()
                    val chatId = call.parameters["chatId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorApiResponse("Missing chatId parameter")
                    )
                    val userIdToAdd = call.parameters["userId"] ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorApiResponse("Missing userId parameter")
                    )
                    routingLog.info("User {} adding user {} to chat {}", addedByUserId, userIdToAdd, chatId)
                    val natsReq = NatsAddUserToChatRequest(addedByUserId, chatId, userIdToAdd)
                    val result = nats.request<NatsAddUserToChatRequest, NatsChat>("chat.member.add", natsReq)
                    call.handleNatsResult(result) { it.toApi(addedByUserId) } // NatsChat.toApi()
                }
            }

            // --- WebSocket для чата ---
            // Логика WebSocket остается прежней, так как она не использует NatsClient напрямую
            // и должна обрабатывать ошибки отправки/получения внутри сессии.
            webSocket("/chat") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                routingLog.info("WebSocket connection established for user: {}", userId)
                var dispatcher: Dispatcher? = null

                try {
                    val broadcastTopic = "chat.broadcast.$userId"
                    dispatcher = natsRaw.createDispatcher { msg ->
                        launch {
                            try {
                                val msgJson = String(msg.data, StandardCharsets.UTF_8)
                                outgoing.send(Frame.Text(msgJson))
                                routingLog.debug("Sent broadcast message to user {} via WebSocket", userId)
                            } catch (e: Exception) {
                                routingLog.error(
                                    "Error sending broadcast to WebSocket for user {}: {}",
                                    userId,
                                    e.message
                                )
                                close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, "Send failed"))
                            }
                        }
                    }.apply { subscribe(broadcastTopic) }
                    routingLog.info("Subscribed to NATS topic: {}", broadcastTopic)

                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            routingLog.debug("Received message from user {} via WebSocket", userId)
                            try {
                                val clientMsg = json.decodeFromString<ChatMessageApiRequest>(text)
                                val natsMsg =
                                    NatsIncomingMessage(userId, clientMsg.chatId, clientMsg.type, clientMsg.content)
                                natsRaw.publish("chat.message.incoming", json.encodeToBytes(natsMsg))
                                routingLog.debug("Published incoming message to NATS topic 'chat.message.incoming'")
                            } catch (e: Exception) {
                                routingLog.error(
                                    "Error processing incoming WS message from user {}: {}",
                                    userId,
                                    e.message
                                )
                                try {
                                    outgoing.send(Frame.Text(String(json.encodeToBytes(ErrorApiResponse("Invalid message format: ${e.message}")))))
                                } catch (sendError: Exception) {
                                    routingLog.error(
                                        "Failed to send error back to user {}: {}",
                                        userId,
                                        sendError.message
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    routingLog.error("Error in WebSocket session for user {}: {}", userId, e.message, e)
                    close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, e.localizedMessage ?: "WebSocket error"))
                } finally {
                    routingLog.info("WebSocket connection closed for user: {}. Closing NATS dispatcher.", userId)
                    dispatcher?.let { natsRaw.closeDispatcher(it) }
                }
            }
        }
    }
}