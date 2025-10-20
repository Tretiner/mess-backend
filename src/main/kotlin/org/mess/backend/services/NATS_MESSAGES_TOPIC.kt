package org.mess.backend.services

import io.nats.client.Connection
import io.nats.client.Nats
import java.nio.charset.StandardCharsets

// Тема NATS, куда мы публикуем все сообщения
const val NATS_MESSAGES_TOPIC = "chat.messages"

class NatsService(private val natsUrl: String) {

    private var natsConnection: Connection? = null

    fun connect() {
        try {
            natsConnection = Nats.connect(natsUrl)
            println("✅ Connected to NATS at $natsUrl")
        } catch (e: Exception) {
            println("❌ Failed to connect to NATS: ${e.message}")
            // В production здесь нужен retry
        }
    }

    // Отправка сообщения (JSON-строка) в NATS
    fun publishMessage(messageJson: String) {
        natsConnection?.publish(NATS_MESSAGES_TOPIC, messageJson.toByteArray(StandardCharsets.UTF_8))
    }

    // Подписка на сообщения из NATS
    fun subscribeToMessages(onMessageReceived: (String) -> Unit) {
        val dispatcher = natsConnection?.createDispatcher { msg ->
            val json = String(msg.data, StandardCharsets.UTF_8)
            onMessageReceived(json)
        }
        dispatcher?.subscribe(NATS_MESSAGES_TOPIC)
    }

    fun close() {
        natsConnection?.close()
    }
}