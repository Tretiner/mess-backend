// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/services/NatsClient.kt
package org.mess.backend.gateway.services

import io.ktor.http.*
import io.nats.client.Connection
import kotlinx.coroutines.future.await
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.mess.backend.core.DefaultJson
import org.mess.backend.core.NatsErrorResponse // Используем общую модель ошибки из core
import org.mess.backend.core.decodeFromBytes // Используем общие расширения из core
import org.mess.backend.core.encodeToBytes
import org.mess.backend.gateway.exceptions.ServiceException // Наше кастомное исключение для передачи статуса и сообщения
import org.slf4j.LoggerFactory // Используем SLF4J для логирования
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.TimeoutException

// Получаем логгер для этого класса
val log = LoggerFactory.getLogger(NatsClient::class.java)

/**
 * Хелпер для выполнения NATS Request-Reply запросов к другим микросервисам.
 * Инкапсулирует логику сериализации/десериализации JSON и обработки ошибок NATS/сервисов.
 * Возвращает Result<R> для явной обработки успеха или неудачи.
 */
class NatsClient(
    val nats: Connection,
    val json: Json = DefaultJson // Используем общий Json инстанс из core или Application.kt
) {
    /**
     * Отправляет запрос к другому сервису по NATS и возвращает Result с ответом или ошибкой.
     * @param T Тип объекта запроса (должен быть @Serializable).
     * @param R Тип объекта успешного ответа (должен быть @Serializable).
     * @param topic Тема NATS для отправки запроса (например, "auth.login").
     * @param request Объект запроса типа T.
     * @param timeout Максимальное время ожидания ответа.
     * @return Result<R>, содержащий либо успешный ответ типа R, либо ServiceException с деталями ошибки.
     */
    suspend inline fun <reified T : Any, reified R : Any> request(
        topic: String,
        request: T,
        timeout: Duration = Duration.ofSeconds(5) // Таймаут по умолчанию
    ): Result<R> { // <-- Возвращаем Result<R>
        // 1. Сериализуем запрос
        val requestBytes = try {
            json.encodeToBytes(request) // Используем encodeToBytes из core
        } catch (e: SerializationException) {
            log.error("BUG: Failed to serialize NATS request for topic '{}': {}", topic, e.message)
            return Result.failure(ServiceException(HttpStatusCode.InternalServerError, "Internal error: Failed to serialize request for '$topic'"))
        }

        log.debug("Sending NATS request to '{}'. Size: {} bytes", topic, requestBytes.size)

        // 2. Отправляем NATS запрос и получаем данные ответа
        val replyData = try {
            val future = nats.requestWithTimeout(topic, requestBytes, timeout)
            future.await().data // Получаем ByteArray ответа
        } catch (e: TimeoutException) {
            log.error("NATS request to '{}' timed out after {} seconds.", topic, timeout.seconds)
            return Result.failure(ServiceException(HttpStatusCode.GatewayTimeout, "Service '$topic' did not respond."))
        } catch (e: Exception) {
            log.error("NATS request to '{}' failed unexpectedly: {}", topic, e.message, e)
            return Result.failure(ServiceException(HttpStatusCode.ServiceUnavailable, "Communication error with service '$topic'."))
        }

        log.debug("Received NATS reply from '{}'. Size: {} bytes", topic, replyData.size)

        // 3. Пытаемся десериализовать ответ
        try {
            // Попытка 1: Десериализовать как УСПЕШНЫЙ ответ (тип R)
            val successResponse = json.decodeFromBytes<R>(replyData) // Используем decodeFromBytes из core
            return Result.success(successResponse) // Возвращаем успех
        } catch (e: SerializationException) {
            log.warn("Failed to parse NATS response from '{}' as {}. Trying as NatsErrorResponse...", topic, R::class.simpleName)
            try {
                // Попытка 2: Десериализовать как ОШИБКУ (NatsErrorResponse)
                val errorResponse = json.decodeFromBytes<NatsErrorResponse>(replyData) // Используем decodeFromBytes из core
                log.warn("Service '{}' returned an error: {}", topic, errorResponse.error)

                // Определяем HTTP статус для клиента на основе темы NATS
                val clientStatusCode = when {
                    topic.startsWith("auth.") -> HttpStatusCode.Unauthorized // Ошибки аутентификации
                    // Можно добавить другие правила, например:
                    // topic.contains("notfound") -> HttpStatusCode.NotFound
                    else -> HttpStatusCode.BadRequest // Общая ошибка запроса для других случаев
                }
                // Возвращаем ошибку с кодом и сообщением от сервиса
                return Result.failure(ServiceException(clientStatusCode, errorResponse.error))

            } catch (e2: Exception) {
                // Попытка 3: Не удалось распарсить ни как успех, ни как ошибку NATS
                val bodySample = String(replyData.take(100).toByteArray(), StandardCharsets.UTF_8) // Логгируем только часть тела
                log.error(
                    "Failed to parse response (as {} or NatsErrorResponse) from '{}'. Body sample: '{}'. Error: {}",
                    R::class.simpleName, topic, bodySample.replace("\n", ""), e2.message
                )
                // Возвращаем ошибку 500 Internal Server Error
                return Result.failure(ServiceException(HttpStatusCode.InternalServerError, "Invalid response format received from service '$topic'."))
            }
        } catch (e: Exception) {
            // Другие непредвиденные ошибки при парсинге
            log.error("Unexpected error parsing response from '{}': {}", topic, e.message, e)
            return Result.failure(ServiceException(HttpStatusCode.InternalServerError, "Error processing response from '$topic'."))
        }
    }
}