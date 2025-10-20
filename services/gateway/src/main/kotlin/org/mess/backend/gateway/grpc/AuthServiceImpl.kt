package org.mess.backend.gateway.grpc

import io.nats.client.Connection
import kotlinx.serialization.json.Json
import org.mess.backend.grpc.* // Сгенерированный gRPC код
import java.nio.charset.StandardCharsets
import java.time.Duration

// (Нужны data class'ы для NATS JSON)
@kotlinx.serialization.Serializable
data class NatsAuthRequest(val username: String, val password: String)
@kotlinx.serialization.Serializable
data class NatsAuthResponse(val token: String, val profile: NatsUserProfile)
@kotlinx.serialization.Serializable
data class NatsUserProfile(val id: String, val nickname: String, val avatarUrl: String?)


class AuthServiceImpl(
    private val nats: Connection,
    private val json: Json
) : AuthServiceGrpcKt.AuthServiceCoroutineImplBase() { // Используем Coroutine-версию

    override suspend fun register(request: AuthRequest): AuthResponse {
        // 1. Конвертируем gRPC-запрос в NATS (JSON)
        val natsRequest = NatsAuthRequest(request.username, request.password)
        val requestJson = json.encodeToString(NatsAuthRequest.serializer(), natsRequest)

        println("Sending NATS Req/Reply to auth.register")

        // 2. Отправляем NATS Request-Reply и ждем ответа
        val natsReply = nats.request(
            "auth.register",
            requestJson.toByteArray(StandardCharsets.UTF_8),
            Duration.ofSeconds(5)
        )

        // 3. Парсим NATS-ответ (JSON)
        val replyJson = String(natsReply.data, StandardCharsets.UTF_8)
        val natsResponse = json.decodeFromString(NatsAuthResponse.serializer(), replyJson)

        // 4. Конвертируем NATS-ответ в gRPC-ответ
        return AuthResponse.newBuilder().apply {
            token = natsResponse.token
            profile = UserProfile.newBuilder().apply {
                id = natsResponse.profile.id
                nickname = natsResponse.profile.nickname
                natsResponse.profile.avatarUrl?.let { avatarUrl = it }
            }.build()
        }.build()
    }

    override suspend fun login(request: AuthRequest): AuthResponse {
        // Логика аналогична register, но тема "auth.login"
        // ... (пропущено для краткости)
        return super.login(request)
    }
}