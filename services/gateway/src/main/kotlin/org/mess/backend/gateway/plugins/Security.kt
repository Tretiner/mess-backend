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
import org.mess.backend.gateway.log
import org.mess.backend.gateway.models.api.ErrorApiResponse
import org.slf4j.LoggerFactory

// Плагин Ktor Authentication для проверки JWT
fun Application.configureSecurity(config: JwtConfig) {
    install(Authentication) {
        jwt("auth-jwt") { // Имя конфигурации (может быть несколько)
            realm = config.realm // Используется в WWW-Authenticate заголовке

            // Настраиваем верификатор JWT
            verifier(
                JWT.require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience) // Проверяем аудиторию
                    .withIssuer(config.issuer)     // Проверяем издателя
                    .build()
            )

            // Логика валидации после успешной проверки подписи и стандартных claims
            validate { credential ->
                // Проверяем наличие обязательного claim "userId"
                if (credential.payload.getClaim("userId").asString() != null) {
                    this@configureSecurity.log.debug("JWT validation successful for user: {}", credential.payload.getClaim("userId").asString())
                    JWTPrincipal(credential.payload) // Возвращаем principal, если токен валиден
                } else {
                    this@configureSecurity.log.warn("JWT validation failed: Missing 'userId' claim.")
                    null // Отклоняем токен, если claim отсутствует
                }
            }

            // Что делать, если токен не предоставлен, невалиден или validate вернул null
            challenge { defaultScheme, realm ->
                this@configureSecurity.log.warn("JWT challenge triggered. Scheme: {}, Realm: {}", defaultScheme, realm)
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorApiResponse("Token is not valid or has expired")
                )
            }
        }
    }
}