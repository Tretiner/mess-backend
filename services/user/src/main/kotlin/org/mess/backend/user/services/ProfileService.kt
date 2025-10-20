package org.mess.backend.user.services

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.user.db.UserProfilesTable
import org.mess.backend.user.models.NatsSearchResponse
import org.mess.backend.user.models.NatsUserProfile
import java.util.*

class ProfileService {

    // Хелпер для конвертации ResultRow (БД) -> NatsUserProfile (JSON)
    private fun toNatsUserProfile(row: ResultRow): NatsUserProfile {
        return NatsUserProfile(
            id = row[UserProfilesTable.id].value.toString(),
            nickname = row[UserProfilesTable.nickname],
            avatarUrl = row[UserProfilesTable.avatarUrl]
        )
    }

    /**
     * Вызывается, когда мы получаем событие `user.created`
     */
    fun createProfile(userId: UUID, username: String) {
        transaction {
            // insertIgnore - на случай, если событие придет дважды
            UserProfilesTable.insertIgnore {
                it[id] = userId // ВАЖНО: мы *устанавливаем* ID из события
                it[nickname] = username // По умолчанию ник = логин
                it[avatarUrl] = null
            }
        }
    }

    /**
     * Вызывается для `user.profile.get`
     */
    fun getProfile(userId: UUID): NatsUserProfile? {
        return transaction {
            UserProfilesTable.select { UserProfilesTable.id eq userId }
                .firstOrNull()
                ?.let { toNatsUserProfile(it) }
        }
    }

    /**
     * Вызывается для `user.profile.update`
     */
    fun updateProfile(userId: UUID, newNickname: String?, newAvatarUrl: String?): NatsUserProfile? {
        transaction {
            UserProfilesTable.update({ UserProfilesTable.id eq userId }) {
                // Обновляем только те поля, которые были переданы
                if (newNickname != null) {
                    it[nickname] = newNickname
                }
                if (newAvatarUrl != null) {
                    it[avatarUrl] = newAvatarUrl
                }
            }
        }
        // Возвращаем обновленный профиль
        return getProfile(userId)
    }

    /**
     * Вызывается для `user.search`
     */
    fun searchProfiles(query: String): NatsSearchResponse {
        val users = transaction {
            UserProfilesTable.select { UserProfilesTable.nickname like "%$query%" }
                .limit(20)
                .map { toNatsUserProfile(it) }
        }
        return NatsSearchResponse(users)
    }
}