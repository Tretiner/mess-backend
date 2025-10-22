package org.mess.backend.user.services

import `import org`.jetbrains.exposed.sql.selectAll
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.user.db.UserProfilesTable
import org.mess.backend.user.models.NatsSearchResponse
import org.mess.backend.user.models.NatsUserProfile
import java.util.*

class ProfileService {

    private fun toNatsUserProfile(row: ResultRow): NatsUserProfile {
        return NatsUserProfile(
            id = row[UserProfilesTable.id].value.toString(),
            username = row[UserProfilesTable.username],
            avatarUrl = row[UserProfilesTable.avatarUrl],
            email = row[UserProfilesTable.email],
            fullName = row[UserProfilesTable.fullName]
        )
    }

    /** Создает профиль по событию user.created */
    suspend fun createProfile(userId: UUID, initialUsername: String) {
        newSuspendedTransaction {
            UserProfilesTable.insertIgnore {
                it[id] = userId
                it[username] = initialUsername
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    /** Получает профиль по ID */
    suspend fun getProfile(userId: UUID): NatsUserProfile? {
        return newSuspendedTransaction {
            UserProfilesTable.selectAll().where { UserProfilesTable.id eq userId }
                .firstOrNull()
                ?.let { toNatsUserProfile(it) }
        }
    }

    /**
     * НОВЫЙ МЕТОД: Получает список профилей одним запросом
     */
    suspend fun getProfilesBatch(userIds: List<UUID>): List<NatsUserProfile> {
        if (userIds.isEmpty()) return emptyList()

        return newSuspendedTransaction {
            UserProfilesTable.selectAll().where { UserProfilesTable.id inList userIds }
                .map { toNatsUserProfile(it) }
        }
    }

    /** Обновляет профиль пользователя */
    suspend fun updateProfile(
        userId: UUID,
        newUsername: String?,
        newAvatarUrl: String?,
        newEmail: String?,
        newFullName: String?
    ): NatsUserProfile? {
        // Мы используем newSuspendedTransaction для асинхронной работы
        val updatedRows = newSuspendedTransaction {
            // Проверка уникальности
            if (newUsername != null) {
                val existing = UserProfilesTable.selectAll().where {
                    (UserProfilesTable.username.lowerCase() eq newUsername.lowercase()) and
                            (UserProfilesTable.id neq userId)
                }.count()
                if (existing > 0) {
                    throw Exception("Username '$newUsername' is already taken.")
                }
            }
            if (newEmail != null) {
                val existing = UserProfilesTable.selectAll().where {
                    (UserProfilesTable.email.lowerCase() eq newEmail.lowercase()) and
                            (UserProfilesTable.id neq userId)
                }.count()
                if (existing > 0) {
                    throw Exception("Email '$newEmail' is already taken.")
                }
            }

            // Выполнение обновления
            UserProfilesTable.update({ UserProfilesTable.id eq userId }) {
                newUsername?.let { un -> it[username] = un }
                newAvatarUrl?.let { av -> it[avatarUrl] = av }
                newEmail?.let { em -> it[email] = em }
                newFullName?.let { fn -> it[fullName] = fn }
                it[updatedAt] = Clock.System.now()
            }
        }
        return if (updatedRows > 0) getProfile(userId) else null
    }

    /** Ищет профили по имени, полному имени или email */
    suspend fun searchProfiles(query: String): NatsSearchResponse {
        val searchQuery = "%${query.lowercase()}%"
        val users = newSuspendedTransaction {
            UserProfilesTable.selectAll().where {
                (UserProfilesTable.username.lowerCase() like searchQuery) or
                        (UserProfilesTable.fullName.lowerCase() like searchQuery) or
                        (UserProfilesTable.email.lowerCase() like searchQuery)
            }
                .limit(20)
                .map { toNatsUserProfile(it) }
        }
        return NatsSearchResponse(users)
    }
}