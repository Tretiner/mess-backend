package org.mess.backend.user.services

import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mess.backend.user.db.UserProfilesTable
import org.mess.backend.user.models.NatsSearchResponse
import org.mess.backend.user.models.NatsUserProfile
import java.util.*

class ProfileService {

    // Хелпер конвертации ResultRow -> NatsUserProfile
    private fun toNatsUserProfile(row: ResultRow): NatsUserProfile {
        return NatsUserProfile(
            id = row[UserProfilesTable.id].value.toString(),
            username = row[UserProfilesTable.nickname],
            avatarUrl = row[UserProfilesTable.avatarUrl],
            // --- НОВЫЕ ПОЛЯ ---
            email = row[UserProfilesTable.email],
            fullName = row[UserProfilesTable.fullName]
            // --- КОНЕЦ НОВЫХ ПОЛЕЙ ---
        )
    }

    /**
     * Создает профиль при получении события `user.created`.
     * Инициализирует nickname = username, остальные поля null.
     */
    fun createProfile(userId: UUID, username: String) {
        transaction {
            UserProfilesTable.insertIgnore {
                it[id] = userId
                it[nickname] = username // nickname = username по умолчанию
                // email, fullName, avatarUrl остаются null по умолчанию
                it[createdAt] = Clock.System.now()
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    /**
     * Получает профиль пользователя по ID.
     */
    fun getProfile(userId: UUID): NatsUserProfile? {
        return transaction {
            UserProfilesTable.select { UserProfilesTable.id eq userId }
                .firstOrNull()
                ?.let { toNatsUserProfile(it) }
        }
    }

    /**
     * Обновляет профиль пользователя.
     * Поля в запросе, равные null, игнорируются.
     */
    fun updateProfile(
        userId: UUID,
        newUsername: String?,
        newAvatarUrl: String?,
        newEmail: String?,
        newFullName: String?
    ): NatsUserProfile? {
        val updatedRows = transaction {
            UserProfilesTable.update({ UserProfilesTable.id eq userId }) {
                // Обновляем только не-null поля
                newUsername?.let { nn -> it[nickname] = nn }
                newAvatarUrl?.let { av -> it[avatarUrl] = av }
                newEmail?.let { em -> it[email] = em }
                newFullName?.let { fn -> it[fullName] = fn }
                it[updatedAt] = Clock.System.now() // Обновляем время
            }
        }
        // Возвращаем обновленный профиль, если обновление произошло
        return if (updatedRows > 0) getProfile(userId) else null
    }
    
    /**
     * Ищет пользователей по nickname, fullName или email.
     * Поиск регистронезависимый.
     */
    fun searchProfiles(query: String): NatsSearchResponse {
        // Приводим поисковый запрос к нижнему регистру и добавляем wildcard (%)
        val searchQuery = "%${query.lowercase()}%"
        val users = transaction {
            UserProfilesTable.select {
                // Ищем совпадения в любом из трех полей (приведенных к нижнему регистру)
                (UserProfilesTable.nickname.lowerCase() like searchQuery) or
                        (UserProfilesTable.fullName.lowerCase() like searchQuery) or
                        (UserProfilesTable.email.lowerCase() like searchQuery)
            }
                .limit(20) // Ограничиваем количество результатов
                .map { toNatsUserProfile(it) } // Конвертируем в NATS-модель
        }
        return NatsSearchResponse(users)
    }
}