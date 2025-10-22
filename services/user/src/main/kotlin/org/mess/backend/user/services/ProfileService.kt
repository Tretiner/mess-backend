// FILE: services/user/src/main/kotlin/org/mess/backend/user/services/ProfileService.kt
package org.mess.backend.user.services

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.user.db.UserProfilesTable
import org.mess.backend.user.models.NatsSearchResponse
import org.mess.backend.user.models.NatsUserProfile
import java.util.*

class ProfileService {

    // Helper: ResultRow -> NatsUserProfile
    private fun toNatsUserProfile(row: ResultRow): NatsUserProfile {
        return NatsUserProfile(
            id = row[UserProfilesTable.id].value.toString(),
            username = row[UserProfilesTable.username], // <-- RENAMED from nickname
            avatarUrl = row[UserProfilesTable.avatarUrl],
            email = row[UserProfilesTable.email],
            fullName = row[UserProfilesTable.fullName]
        )
    }

    /** Creates profile on user.created event */
    fun createProfile(userId: UUID, initialUsername: String) {
        transaction {
            UserProfilesTable.insertIgnore {
                it[id] = userId
                it[username] = initialUsername // <-- RENAMED from nickname, use initialUsername
                // email, fullName, avatarUrl remain null
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    /** Gets profile by ID */
    fun getProfile(userId: UUID): NatsUserProfile? {
        return transaction {
            UserProfilesTable.select { UserProfilesTable.id eq userId }
                .firstOrNull()
                ?.let { toNatsUserProfile(it) }
        }
    }

    /** Updates user profile */
    fun updateProfile(
        userId: UUID,
        newUsername: String?, // <-- RENAMED from newNickname
        newAvatarUrl: String?,
        newEmail: String?,
        newFullName: String?
    ): NatsUserProfile? {
        // Basic validation (example)
        if (newUsername != null && newUsername.length < 3) {
            // In a real app, throw a specific exception caught by Application.kt
            println("Validation failed: Username too short")
            return null // Or throw
        }
        // TODO: Add email validation if newEmail is not null

        val updatedRows = transaction {
            // Check if new username is already taken by another user
            if (newUsername != null) {
                val existing = UserProfilesTable.select {
                    (UserProfilesTable.username.lowerCase() eq newUsername.lowercase()) and
                            (UserProfilesTable.id neq userId) // Exclude the current user
                }.count()
                if (existing > 0) {
                    // Throw an exception or handle appropriately in Application.kt
                    println("Validation failed: Username '$newUsername' already taken.")
                    // To make NatsClient return a specific error, throw ServiceException or similar
                    // throw ServiceException(HttpStatusCode.Conflict, "Username '$newUsername' is already taken.")
                    return@transaction 0 // Indicate failure
                }
            }
            // Similar check for email uniqueness if newEmail is provided

            // Perform the update
            UserProfilesTable.update({ UserProfilesTable.id eq userId }) {
                newUsername?.let { un -> it[username] = un } // <-- RENAMED from nickname
                newAvatarUrl?.let { av -> it[avatarUrl] = av }
                newEmail?.let { em -> it[email] = em }
                newFullName?.let { fn -> it[fullName] = fn }
                it[updatedAt] = Clock.System.now()
            }
        }
        return if (updatedRows > 0) getProfile(userId) else null // Return updated profile only if rows changed
    }

    /** Searches profiles by username, fullName, or email (case-insensitive) */
    fun searchProfiles(query: String): NatsSearchResponse {
        val searchQuery = "%${query.lowercase()}%"
        val users = transaction {
            UserProfilesTable.select {
                (UserProfilesTable.username.lowerCase() like searchQuery) or // <-- RENAMED from nickname
                        (UserProfilesTable.fullName.lowerCase() like searchQuery) or
                        (UserProfilesTable.email.lowerCase() like searchQuery)
            }
                .limit(20)
                .map { toNatsUserProfile(it) }
        }
        return NatsSearchResponse(users)
    }
}