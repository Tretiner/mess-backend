// FILE: gateway/src/main/kotlin/org/mess/backend/gateway/plugins/Security.kt
package org.mess.backend.gateway.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.* // Import HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.mess.backend.gateway.JwtConfig // Конфигурация JWT из Config.kt
import org.mess.backend.gateway.models.api.ErrorApiResponse // Модель для JSON-ответа об ошибке
import org.slf4j.LoggerFactory // Используем SLF4J

// Получаем логгер для этого файла
private val securityLog = LoggerFactory.getLogger("GatewaySecurity")

/**
 * Функция расширения для Application, настраивающая плагин Authentication
 * для проверки JWT Bearer токенов.
 */
fun Application.configureSecurity(config: JwtConfig) {
    // Устанавливаем плагин Authentication
    install(Authentication) {
        // Настраиваем JWT аутентификацию под именем "auth-jwt"
        jwt("auth-jwt") {
            // Realm используется в заголовке WWW-Authenticate при ответе 401
            realm = config.realm

            // Настраиваем верификатор токена с использованием библиотеки auth0-jwt
            verifier(
                JWT
                    .require(Algorithm.HMAC256(config.secret)) // 1. Указываем алгоритм и секрет для проверки подписи
                    .withAudience(config.audience)             // 2. Указываем ожидаемую аудиторию (aud claim)
                    .withIssuer(config.issuer)                 // 3. Указываем ожидаемого издателя (iss claim)
                    .build() // Создаем объект верификатора
            )

            // Логика валидации ПОСЛЕ успешной проверки подписи, срока действия, issuer и audience
            validate { credential ->
                // credential содержит расшифрованное содержимое токена (JWT payload)
                // Проверяем наличие обязательного claim "userId"
                val userId = credential.payload.getClaim("userId").asString()
                if (userId != null) {
                    // Если claim есть, считаем токен валидным и возвращаем JWTPrincipal.
                    // JWTPrincipal делает payload доступным в обработчиках маршрутов через `call.principal<JWTPrincipal>()`
                    securityLog.debug("JWT validation successful for user ID: {}", userId)
                    JWTPrincipal(credential.payload)
                } else {
                    // Если claim "userId" отсутствует, считаем токен невалидным
                    securityLog.warn("JWT validation failed: Missing 'userId' claim in token.")
                    null // Возврат null приведет к вызову блока challenge
                }
            }

            // Конфигурация ответа сервера, если токен:
            // - Отсутствует
            // - Не прошел проверку верификатором (подпись, срок действия, issuer, audience)
            // - Не прошел проверку в блоке validate (вернул null)
            challenge { defaultScheme, realm ->
                securityLog.warn("JWT challenge triggered. Scheme: {}, Realm: {}", defaultScheme, realm)

                // 1. Добавляем заголовок WWW-Authenticate (стандарт для 401)
                // Это важно, чтобы клиенты (как Ktor Client Auth) понимали причину ошибки.
                call.response.header(
                    HttpHeaders.WWWAuthenticate,
                    HttpAuthHeader.Parameterized(AuthScheme.Bearer, mapOf(HttpAuthHeader.Parameters.Realm to realm)).render()
                )

                // 2. Отправляем ответ 401 Unauthorized с JSON-телом ошибки
                call.respond(
                    HttpStatusCode.Unauthorized, // Статус 401
                    ErrorApiResponse("Token is not valid, missing, or has expired") // Сообщение об ошибке
                )
            }
        }
    }
}