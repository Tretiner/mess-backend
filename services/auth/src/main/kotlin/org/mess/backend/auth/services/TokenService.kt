// FILE: services/auth/src/main/kotlin/org/mess/backend/auth/services/TokenService.kt
package org.mess.backend.auth.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import org.mess.backend.auth.JwtConfig
import java.util.*

/**
 * Сервис для создания JWT токенов.
 */
internal class TokenService(private val config: JwtConfig) {

    // Алгоритм подписи, инициализируется один раз
    private val algorithm = Algorithm.HMAC256(config.secret)

    /**
     * Создает JWT токен для указанного пользователя.
     * @param userId UUID пользователя.
     * @param username Имя пользователя (логин).
     * @return Сгенерированный JWT токен в виде строки.
     */
    fun createToken(userId: UUID, username: String): String {
        return JWT.create()
            .withAudience(config.audience) // Для кого предназначен токен
            .withIssuer(config.issuer)     // Кто выдал токен
            .withClaim("userId", userId.toString()) // Основной идентификатор
            .withClaim("username", username) // Дополнительная информация (может быть полезна)
            .withExpiresAt(Date(System.currentTimeMillis() + config.validityMs)) // Время жизни токена
            .sign(algorithm) // Подписываем токен
    }
}