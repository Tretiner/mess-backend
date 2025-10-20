package org.mess.backend.gateway.exceptions

import io.ktor.http.*

/**
 * Кастомное исключение для передачи ошибок от внутренних сервисов.
 * @param statusCode HTTP-статус, который нужно вернуть клиенту.
 * @param message Сообщение об ошибке.
 */
class ServiceException(
    val statusCode: HttpStatusCode,
    override val message: String
) : RuntimeException(message)