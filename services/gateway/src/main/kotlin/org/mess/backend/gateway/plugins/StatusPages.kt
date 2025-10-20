// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/StatusPages.kt
package org.mess.backend.gateway.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException
import org.mess.backend.gateway.exceptions.ServiceException
import org.mess.backend.gateway.models.api.ErrorApiResponse

// Плагин Ktor StatusPages для централизованной обработки исключений
fun Application.configureStatusPages() {
    install(StatusPages) {
        // Обработка ошибок парсинга JSON от клиента
        exception<SerializationException> { call, cause ->
            this@configureStatusPages.log.warn("Bad Request: Invalid JSON format. {}", cause.localizedMessage)
            call.respond(HttpStatusCode.BadRequest, ErrorApiResponse("Invalid JSON format: ${cause.localizedMessage}"))
        }

        // Обработка ошибок, пришедших от внутренних сервисов через NatsClient
        exception<ServiceException> { call, cause ->
            this@configureStatusPages.log.warn("Service Error: Status={}, Message={}", cause.statusCode, cause.message)
            call.respond(cause.statusCode, ErrorApiResponse(cause.message))
        }

        // Общая обработка всех остальных непредвиденных ошибок
        exception<Throwable> { call, cause ->
            // ВАЖНО: Логируем полное исключение для отладки
            this@configureStatusPages.log.error("Internal Server Error: ", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorApiResponse("An unexpected internal error occurred."))
        }
    }
}