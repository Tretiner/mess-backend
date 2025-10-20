package org.mess.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import org.mess.backend.models.*
import org.mess.backend.services.AuthService
import org.mess.backend.services.ChatService
import org.mess.backend.services.UserService
import java.util.*

fun Application.configureRouting(
    userService: UserService,
    authService: AuthService,
    chatService: ChatService
) {
    routing {
        route("/auth") {
            post("/register") {
                val request = call.receive<AuthRequest>()
                val user = userService.registerUser(request.username, request.password)
                if (user != null) {
                    call.respond(HttpStatusCode.Created, authService.createToken(user.id))
                } else {
                    call.respond(HttpStatusCode.Conflict, mapOf("error" to "User already exists"))
                }
            }
            post("/login") {
                val request = call.receive<AuthRequest>()
                val user = userService.loginUser(request.username, request.password)
                if (user != null) {
                    call.respond(HttpStatusCode.OK, authService.createToken(user.id))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                }
            }
        }

        authenticate("auth-jwt") {

            route("/users") {
                get("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val user = userService.getUserProfile(userId)
                    if (user != null) {
                        call.respond(user)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }

                put("/me") {
                    val principal = call.principal<JWTPrincipal>()!!
                    val userId = principal.payload.getClaim("userId").asString()
                    val request = call.receive<UserProfileUpdateRequest>()
                    val updatedUser = userService.updateUserProfile(userId, request.newNickname, request.newAvatarUrl)
                    call.respond(updatedUser!!)
                }

                get("/search") {
                    val query = call.request.queryParameters["q"]
                    if (query.isNullOrBlank()) {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query 'q' is required"))
                        return@get
                    }
                    val users = userService.searchUsers(query)
                    call.respond(users)
                }
            }

            route("/chats") {
                post("/create/group") {
                    val request = call.receive<ChatCreateGroupRequest>()
                    val principal = call.principal<JWTPrincipal>()!!
                    val creatorId = principal.payload.getClaim("userId").asString()

                    val chat = chatService.createGroupChat(creatorId, request.name, request.memberIds)
                    call.respond(HttpStatusCode.Created, chat)
                }

                post("/create/dm/{otherUserId}") {
                    val otherUserId = call.parameters["otherUserId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val principal = call.principal<JWTPrincipal>()!!
                    val myId = principal.payload.getClaim("userId").asString()

                    val chat = chatService.createDirectMessageChat(myId, otherUserId)
                    call.respond(HttpStatusCode.OK, chat)
                }

                post("/{chatId}/add/{userId}") {
                    val chatId = call.parameters["chatId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val userIdToAdd = call.parameters["userId"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val principal = call.principal<JWTPrincipal>()!!
                    val myId = principal.payload.getClaim("userId").asString()

                    val chat = chatService.addUserToChat(chatId, userIdToAdd, myId)
                    if (chat != null) {
                        call.respond(HttpStatusCode.OK, chat)
                    } else {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Group chat not found or invalid operation"))
                    }
                }
            }

            post("/upload") {
                val fileId = UUID.randomUUID().toString()
                val fileType = call.request.queryParameters["type"] ?: "file"
                val fileUrl = "/files/$fileType/$fileId.dat"
                call.respond(mapOf("url" to fileUrl))
            }

            webSocket("/chat") {
                val principal = call.principal<JWTPrincipal>()!!
                val userId = principal.payload.getClaim("userId").asString()
                val user = userService.getUserProfile(userId) ?: return@webSocket close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "User not found"))

                val session = this
                chatService.userConnected(userId, session)

                try {
                    for (frame in incoming) {
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            // Теперь эта функция парсит JSON и отправляет в NATS
                            chatService.sendMessageToNats(userId, text)
                        }
                    }
                } catch (e: Exception) {
                    println("WS Error: ${e.localizedMessage}")
                } finally {
                    chatService.userDisconnected(userId)
                }
            }
        }
    }
}