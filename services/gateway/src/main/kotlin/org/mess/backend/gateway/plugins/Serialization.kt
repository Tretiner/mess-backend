// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/Serialization.kt
package org.mess.backend.gateway.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import org.mess.backend.gateway.json // Используем наш глобальный Json инстанс

// Плагин Ktor ContentNegotiation для автоматической сериализации/десериализации JSON
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        // Используем kotlinx.serialization с нашими глобальными настройками
        json(json)
    }
}