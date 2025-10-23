package org.mess.backend.auth.services

import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.auth.db.AuthUsersTable
import org.mess.backend.auth.models.NatsUserProfileStub
import org.mindrot.jbcrypt.BCrypt
import java.util.*

// --- УПРОЩЕННЫЕ Result classes ---
data class RegistrationResult(
    val accessToken: String?,
    val profile: NatsUserProfileStub?,
    val userId: UUID?
)

data class LoginResult(
    val accessToken: String?,
    val profile: NatsUserProfileStub?
)
// RefreshResult УДАЛЕН.

class AuthService(private val tokenService: TokenService) {

    fun registerUser(username: String, password: String): RegistrationResult {
        return transaction {
            val existing =
                AuthUsersTable.selectAll().where { AuthUsersTable.username.lowerCase() eq username.lowercase() }.count()
            if (existing > 0) return@transaction RegistrationResult(null, null, null)

            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())
            val newUserId = AuthUsersTable.insertAndGetId {
                it[AuthUsersTable.username] = username
                it[AuthUsersTable.passwordHash] = passwordHash
            }.value

            // --- Генерация ТОЛЬКО Access Token ---
            val accessToken = tokenService.createAccessToken(newUserId, username)
            // ---

            val profileStub = NatsUserProfileStub(id = newUserId.toString(), username = username)
            RegistrationResult(accessToken, profileStub, newUserId)
        }
    }

    fun loginUser(username: String, password: String): LoginResult {
        return transaction {
            val row = AuthUsersTable.selectAll().where { AuthUsersTable.username.lowerCase() eq username.lowercase() }
                .firstOrNull()
                ?: return@transaction LoginResult(null, null)

            val userId = row[AuthUsersTable.id].value
            val storedUsername = row[AuthUsersTable.username]
            val hash = row[AuthUsersTable.passwordHash]

            if (BCrypt.checkpw(password, hash)) {
                // --- Генерация ТОЛЬКО Access Token ---
                val accessToken = tokenService.createAccessToken(userId, storedUsername)
                // ---

                val profileStub = NatsUserProfileStub(id = userId.toString(), username = storedUsername)
                LoginResult(accessToken, profileStub)
            } else {
                LoginResult(null, null)
            }
        }
    }

    // refreshAccessToken УДАЛЕН.
}