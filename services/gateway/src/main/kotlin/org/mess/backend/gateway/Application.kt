package org.mess.backend.gateway

import io.grpc.Server
import io.grpc.ServerBuilder
import io.nats.client.Connection
import io.nats.client.Nats
import kotlinx.serialization.json.Json
import org.mess.backend.gateway.grpc.AuthServiceImpl
import org.mess.backend.gateway.grpc.ChatServiceImpl
import org.mess.backend.gateway.grpc.UserServiceImpl

// Модели JSON, которые мы гоняем по NATS (они должны совпадать с моделями в других сервисах)
// ... (здесь должны быть data class'ы, например, NatsAuthRequest и т.д.)

val json = Json { ignoreUnknownKeys = true }

fun main() {
    val natsPort = System.getenv("NATS_PORT") ?: "4222"
    val natsHost = System.getenv("NATS_HOST") ?: "nats"
    val natsConnection: Connection = Nats.connect("nats://$natsHost:$natsPort")
    println("Gateway connected to NATS")

    val grpcPort = 8080

    // Создаем gRPC сервисы, передавая им NATS-клиент
    val authService = AuthServiceImpl(natsConnection, json)
    val userService = UserServiceImpl(natsConnection, json)
    val chatService = ChatServiceImpl(natsConnection, json)

    val server: Server = ServerBuilder.forPort(grpcPort)
        .addService(authService)
        .addService(userService)
        .addService(chatService)
        .build()

    server.start()
    println("gRPC Gateway started on port $grpcPort")
    server.awaitTermination()
}