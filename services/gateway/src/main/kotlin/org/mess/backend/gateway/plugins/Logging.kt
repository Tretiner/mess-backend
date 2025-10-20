// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/Logging.kt
package org.mess.backend.gateway.plugins

import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.request.*
import org.slf4j.event.Level

// Плагин для логирования входящих HTTP-запросов
fun Application.configureLogging() {
    install(CallLogging) {
        level = Level.DEBUG
        // Формат лога
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.httpMethod.value
            val userAgent = call.request.headers["User-Agent"]
            "Status: $status, HTTP Method: $httpMethod, Path: ${call.request.path()}, User Agent: $userAgent"
        }
    }
}