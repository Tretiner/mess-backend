// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/Security.kt
package org.mess.backend.gateway.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.mess.backend.gateway.JwtConfig
import org.mess.backend.gateway.log // Используем глобальный логгер
import org.mess.backend.gateway.models.api.ErrorApiResponse
import org.slf4j.LoggerFactory

// Плагин Ktor Authentication для проверки JWT
fun Application.configureSecurity(config: JwtConfig) {
    // Устанавливаем плагин Authentication
    install(Authentication) {
        // Настраиваем провайдер JWT с именем "auth-jwt"
        jwt("auth-jwt") {
            val log = LoggerFactory.getLogger("JWT-Security")

            realm = config.realm // Используется в WWW-Authenticate заголовке при ошибке 401

            // Настраиваем верификатор JWT с использованием секрета, издателя и аудитории
            verifier(
                JWT.require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience) // Проверяем, что токен предназначен для нашего сервиса
                    .withIssuer(config.issuer)     // Проверяем, что токен выдан нашим auth-сервисом
                    .build()
            )

            // Дополнительная логика валидации после успешной проверки подписи и стандартных claims
            validate { credential ->
                // Проверяем наличие обязательного claim "userId"
                val userId = credential.payload.getClaim("userId").asString()
                if (userId != null) {
                    log.debug("JWT validation successful for user ID: {}", userId)
                    JWTPrincipal(credential.payload) // Возвращаем principal, если токен валиден
                } else {
                    log.warn("JWT validation failed: Missing 'userId' claim in token.")
                    null // Отклоняем токен, если claim отсутствует
                }
            }

            // Конфигурация ответа сервера, если токен не предоставлен, невалиден,
            // истек срок действия, или validate вернул null
            challenge { defaultScheme, realm ->
                log.warn("JWT challenge triggered. Scheme: {}, Realm: {}", defaultScheme, realm)
                // Отправляем 401 Unauthorized с JSON-телом ошибки
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorApiResponse("Token is not valid, missing, or has expired")
                )
            }
        }
    }
}