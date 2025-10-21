// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/services/NatsClient.kt
package org.mess.backend.gateway.services

import io.ktor.http.*
import io.nats.client.Connection
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.mess.backend.core.NatsErrorResponse
import org.mess.backend.gateway.exceptions.ServiceException
import org.mess.backend.gateway.log // Используем глобальный логгер
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 * Хелпер для выполнения NATS Request-Reply запросов к другим микросервисам.
 * Инкапсулирует логику сериализации/десериализации JSON и обработки ошибок NATS/сервисов.
 */
class NatsClient(
    val nats: Connection,
    val json: Json
) {
    /**
     * Отправляет запрос к другому сервису по NATS и ожидает ответ.
     * @param T Тип объекта запроса (должен быть @Serializable).
     * @param R Тип объекта успешного ответа (должен быть @Serializable).
     * @param topic Тема NATS для отправки запроса (например, "auth.login").
     * @param request Объект запроса типа T.
     * @param timeout Максимальное время ожидания ответа.
     * @return Объект успешного ответа типа R.
     * @throws ServiceException если сервис вернул ошибку (NatsErrorResponse),
     * произошел таймаут NATS, ответ не удалось распарсить или произошла другая ошибка NATS/сериализации.
     */
    suspend inline fun <reified T : Any, reified R : Any> request(
        topic: String,
        request: T,
        timeout: Duration = Duration.ofSeconds(5) // Таймаут по умолчанию 5 секунд
    ): Result<R> {
        // 1. Сериализуем запрос в JSON
        val requestJson = try {
            json.encodeToString<T>(request)
        } catch (e: SerializationException) {
            log.error("BUG: Failed to serialize NATS request for topic '{}': {}", topic, e.message)
            // Это ошибка программирования (неверная модель T), бросаем 500
            throw ServiceException(HttpStatusCode.InternalServerError, "Internal error: Failed to serialize request for '$topic'")
        }

        log.debug("Sending NATS request to '{}'. Body: {}", topic, requestJson) // Осторожно с PII в логах

        // 2. Отправляем NATS запрос и ждем ответа
        val reply = try {
            val future = nats.requestWithTimeout(
                topic,
                requestJson.toByteArray(StandardCharsets.UTF_8),
                timeout
            )
            future.await() // Используем await из kotlinx-coroutines-future
        } catch (e: TimeoutException) {
            log.error("NATS request to '{}' timed out after {} seconds.", topic, timeout.seconds)
            throw ServiceException(
                HttpStatusCode.GatewayTimeout, // 504 Gateway Timeout
                "Service '$topic' did not respond within ${timeout.seconds} seconds."
            )
        } catch (e: Exception) {
            // Другие ошибки NATS (например, нет подписчиков, ошибка подключения к NATS серверу)
            log.error("NATS request to '{}' failed unexpectedly: {}", topic, e.message, e)
            throw ServiceException(
                HttpStatusCode.ServiceUnavailable, // 503 Service Unavailable
                "Communication error with service '$topic': ${e.message}"
            )
        }

        // 3. Десериализуем ответ
        val replyJson = String(reply.data, StandardCharsets.UTF_8)
        log.debug("Received NATS reply from '{}'. Body: {}", topic, replyJson)

        // 3a. Пытаемся распарсить как УСПЕШНЫЙ ответ (тип R)
        try {
            return Result.success(json.decodeFromString<R>(replyJson))
        } catch (e: SerializationException) {
            log.warn("Failed to parse successful response from '{}' as {}. Trying to parse as NatsErrorResponse...", topic, R::class.simpleName)
            // 3b. Не получилось как R. Пытаемся распарсить как ОШИБКУ (NatsErrorResponse), которую вернул сервис
            try {
                val errorResponse = json.decodeFromString<NatsErrorResponse>(replyJson)
                log.warn("Service '{}' returned an error: {}", topic, errorResponse.error)
                // Ошибка пришла от внутреннего сервиса, но для клиента это внутренняя ошибка сервера (500)
                // Или можно выбрать другой код, например BadGateway (502), если сервис явно вернул ошибку]
                return Result.failure(Exception(errorResponse.error))
            } catch (e2: Exception) {
                // 3c. Сервис вернул совершенно невалидный JSON (не R и не NatsErrorResponse)
                log.error("Failed to parse response (as {} or NatsErrorResponse) from '{}'. Body: '{}'. Error: {}", R::class.simpleName, topic, replyJson, e2.message)
                throw ServiceException(
                    HttpStatusCode.InternalServerError, // Однозначно внутренняя ошибка
                    "Invalid or unexpected response format received from service '$topic'."
                )
            }
        } catch (e: Exception) {
            // Другие непредвиденные ошибки при парсинге (маловероятно)
            log.error("Unexpected error parsing response from '{}': {}", topic, e.message, e)
            throw ServiceException(
                HttpStatusCode.InternalServerError,
                "Error processing response from '$topic'."
            )
        }
    }
}