// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/services/NatsClient.kt
package org.mess.backend.gateway.services

import io.ktor.http.*
import io.nats.client.Connection
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.mess.backend.gateway.exceptions.ServiceException
import org.mess.backend.gateway.log
import org.mess.backend.gateway.models.nats.NatsErrorResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Хелпер для выполнения NATS Request-Reply запросов к другим микросервисам.
 * Инкапсулирует логику сериализации/десериализации JSON и обработки ошибок.
 */
class NatsClient(
    val nats: Connection,
    val json: Json
) {
    suspend inline fun <reified T : Any, reified R : Any> request(
        topic: String,
        request: T,
        timeout: Duration = Duration.ofSeconds(5) // Таймаут по умолчанию
    ): R {
        val requestJson = try {
            json.encodeToString(serializer<T>(), request)
        } catch (e: SerializationException) {
            log.error("Failed to serialize NATS request for topic '{}': {}", topic, e.message)
            // Это ошибка программирования, бросаем внутреннюю ошибку
            throw ServiceException(HttpStatusCode.InternalServerError, "Failed to serialize request for '$topic'")
        }

        log.debug("Sending NATS request to '{}': {}", topic, requestJson) // Логируем сам запрос (осторожно с PII)

        val reply = try {
            // Асинхронно отправляем запрос и ждем ответа
            val future = nats.requestWithTimeout(
                topic,
                requestJson.toByteArray(StandardCharsets.UTF_8),
                timeout
            )
            future.await() // Используем await из kotlinx-coroutines-future
        } catch (e: TimeoutException) {
            log.error("NATS request to '{}' timed out after {} seconds.", topic, timeout.seconds)
            throw ServiceException(
                HttpStatusCode.GatewayTimeout, // Используем 504 Gateway Timeout
                "Service '$topic' did not respond within ${timeout.seconds} seconds."
            )
        } catch (e: Exception) {
            // Другие ошибки NATS (например, нет подписчиков, ошибка подключения)
            log.error("NATS request to '{}' failed: {}", topic, e.message, e)
            throw ServiceException(
                HttpStatusCode.ServiceUnavailable, // Сервис недоступен
                "Communication error with service '$topic': ${e.message}"
            )
        }

        val replyJson = String(reply.data, StandardCharsets.UTF_8)
        log.debug("Received NATS reply from '{}': {}", topic, replyJson)

        // Пытаемся распарсить как УСПЕШНЫЙ ответ (тип R)
        try {
            return json.decodeFromString(serializer<R>(), replyJson)
        } catch (e: SerializationException) {
            log.warn("Failed to parse successful response from '{}' as {}. Trying to parse as error...", topic, R::class.simpleName)
            // Не получилось. Пытаемся распарсить как ОШИБКУ (NatsErrorResponse), которую вернул сервис
            try {
                val errorResponse = json.decodeFromString(NatsErrorResponse.serializer(), replyJson)
                log.warn("Service '{}' returned an error: {}", topic, errorResponse.error)
                // Используем код InternalServerError, т.к. ошибка произошла во внутреннем сервисе
                throw ServiceException(
                    HttpStatusCode.InternalServerError,
                    errorResponse.error
                )
            } catch (e2: Exception) {
                // Сервис вернул совершенно невалидный JSON
                log.error("Failed to parse response (success or error) from '{}'. Body: '{}'. Error: {}", topic, replyJson, e2.message)
                throw ServiceException(
                    HttpStatusCode.InternalServerError, // Ошибка на стороне сервера (gateway)
                    "Invalid response format received from service '$topic'."
                )
            }
        } catch (e: Exception) {
            // Другие непредвиденные ошибки при парсинге
            log.error("Unexpected error parsing response from '{}': {}", topic, e.message, e)
            throw ServiceException(
                HttpStatusCode.InternalServerError,
                "Error processing response from '$topic'."
            )
        }
    }
}