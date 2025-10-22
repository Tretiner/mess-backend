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

                // GET /chats/{chatId}
                get("/{chatId}") {
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
                put("/{chatId}") {
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
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                routingLog.info("WebSocket connection established for user: {}", userId)
                var dispatcher: Dispatcher? = null

                try {
                    // Подписываемся на ЛИЧНУЮ тему broadcast-ов
                    val broadcastTopic = "chat.broadcast.$userId"
                    dispatcher = natsRaw.createDispatcher { msg ->
                        launch {
                            try {
                                // --- ИЗМЕНЕНИЕ: Маппим NATS -> API ---
                                val natsMsg = json.decodeFromBytes<NatsBroadcastMessage>(msg.data)
                                val apiMsg = natsMsg.toApi() // NatsBroadcastMessage -> BroadcastMessageApiResponse
                                val apiJson = json.encodeToBytes(apiMsg)
                                outgoing.send(Frame.Text(String(apiJson, StandardCharsets.UTF_8)))
                                // --- КОНЕЦ ИЗМЕНЕНИЯ ---

                                routingLog.debug("Sent broadcast message to user {} via WebSocket", userId)
                            } catch (e: Exception) {
                                routingLog.error("Error sending broadcast to WebSocket for user {}: {}", userId, e.message)
                                close(CloseReason(CloseReason.Codes.UNEXPECTED_CONDITION, "Send failed"))
                            }
                        }
                    }.apply { subscribe(broadcastTopic) }
                    routingLog.info("Subscribed to NATS topic: {}", broadcastTopic)

                    // Принимаем сообщения от клиента
                    incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            routingLog.debug("Received message from user {} via WebSocket", userId)
                            try {
                                val clientMsg = json.decodeFromString<ChatMessageApiRequest>(text)
                                val natsMsg =
                                    NatsIncomingMessage(userId, clientMsg.chatId, clientMsg.type, clientMsg.content)
                                // Отправляем в ОБЩУЮ тему "на вход"
                                natsRaw.publish("chat.message.incoming", json.encodeToBytes(natsMsg))
                            } catch (e: Exception) {
                                routingLog.error("Error processing incoming WS message from user {}: {}", userId, e.message)
                                try {
                                    val errorApi = ErrorApiResponse("Invalid message format: ${e.message}")
                                    outgoing.send(Frame.Text(String(json.encodeToBytes(errorApi))))
                                } catch (sendError: Exception) {
                                    routingLog.error("Failed to send error back to user {}: {}", userId, sendError.message)
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