// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/Security.kt
package org.mess.backend.gateway.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.mess.backend.gateway.JwtConfig
import org.mess.backend.gateway.models.api.ErrorApiResponse
import org.slf4j.LoggerFactory

private val securityLog = LoggerFactory.getLogger("GatewaySecurity")

fun Application.configureSecurity(config: JwtConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.realm

            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.secret))
                    .withAudience(config.audience)
                    .withIssuer(config.issuer)
                    .build()
            )

            // --- ИЗМЕНЕНИЕ ---
            // Мы больше не требуем ОБЯЗАТЕЛЬНОГО наличия "userId".
            // Если токен прошел проверку `verifier` (подпись, issuer, audience, срок действия),
            // мы считаем его валидным и просто возвращаем Principal.
            validate { credential ->
                securityLog.debug("JWT passed verifier. Granting principal. Claims present: {}", credential.payload.claims.keys)
                JWTPrincipal(credential.payload)
            }
            // --- КОНЕЦ ИЗМЕНЕНИЯ ---

            challenge { defaultScheme, realm ->
                securityLog.warn("JWT challenge triggered. Scheme: {}, Realm: {}", defaultScheme, realm)
                call.response.header(
                    HttpHeaders.WWWAuthenticate,
                    HttpAuthHeader.Parameterized(AuthScheme.Bearer, mapOf(HttpAuthHeader.Parameters.Realm to realm)).render()
                )
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorApiResponse("Token is not valid, missing, or has expired")
                )
            }
        }
    }
}