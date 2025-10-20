package org.mess.backend.gateway.grpc

import io.nats.client.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.mess.backend.grpc.* // gRPC
import java.nio.charset.StandardCharsets

// (Нужны data class'ы для NATS JSON)
@kotlinx.serialization.Serializable
data class NatsIncomingMessage(val userId: String, val chatId: String, val type: String, val content: String)
@kotlinx.serialization.Serializable
data class NatsBroadcastMessage(val messageId: String, val chatId: String, val sender: NatsUserProfile, val type: String, val content: String, val sentAt: String)

class ChatServiceImpl(
    private val nats: Connection,
    private val json: Json
) : ChatServiceGrpcKt.ChatServiceCoroutineImplBase() {

    private val scope = CoroutineScope(Dispatchers.IO)

    // Unary-вызовы (создание чата) работают по Request-Reply, как в AuthServiceImpl
    // ...

    // Стрим!
    override fun connectChat(requests: Flow<ChatMessageRequest>): Flow<ChatMessageResponse> {
        // Получаем ID пользователя из gRPC-контекста (через JWT-interceptor, здесь пропущено)
        val userId = "USER_ID_FROM_CONTEXT"
        println("Client $userId connected to chat stream")

        // callbackFlow - это мост между callback-миром (NATS) и Flow-миром (gRPC)
        return callbackFlow {

            // 1. ПОДПИСКА (NATS -> gRPC)
            // Мы слушаем ВСЕ сообщения и фильтруем
            val dispatcher = nats.createDispatcher { msg ->
                val msgJson = String(msg.data, StandardCharsets.UTF_8)
                val broadcastMsg = json.decodeFromString(NatsBroadcastMessage.serializer(), msgJson)

                // TODO: Проверить, что этот `userId` состоит в `broadcastMsg.chatId`
                // ...

                // Конвертируем NATS JSON в gRPC Pojo
                val response = ChatMessageResponse.newBuilder().apply {
                    messageId = broadcastMsg.messageId
                    chatId = broadcastMsg.chatId
                    sender = UserProfile.newBuilder().apply {
                        id = broadcastMsg.sender.id
                        nickname = broadcastMsg.sender.nickname
                        broadcastMsg.sender.avatarUrl?.let { avatarUrl = it }
                    }.build()
                    type = broadcastMsg.type
                    content = broadcastMsg.content
                    sentAt = broadcastMsg.sentAt
                }.build()

                // Отправляем сообщение в gRPC-стрим
                trySend(response)
            }
            dispatcher.subscribe("chat.message.broadcast")

            // 2. ПУБЛИКАЦИЯ (gRPC -> NATS)
            // Запускаем корутину, которая слушает gRPC-стрим от клиента
            scope.launch {
                requests.collect { req ->
                    // Клиент прислал сообщение
                    val natsRequest = NatsIncomingMessage(userId, req.chatId, req.type, req.content)
                    val requestJson = json.encodeToString(NatsIncomingMessage.serializer(), natsRequest)

                    // Публикуем его в NATS
                    nats.publish("chat.message.incoming", requestJson.toByteArray(StandardCharsets.UTF_8))
                }
            }

            // 3. ОЧИСТКА
            // Когда gRPC-стрим закроется
            awaitClose {
                println("Client $userId disconnected from chat stream")
                nats.closeDispatcher(dispatcher) // Отписываемся от NATS
            }
        }
    }
}