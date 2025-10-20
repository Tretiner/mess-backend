package org.mess.backend.chat.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction

data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String
)

fun initDatabase(config: DatabaseConfig) {
    Database.connect(
        url = config.url,
        driver = "org.postgresql.Driver",
        user = config.user,
        password = config.password
    )

    transaction {
        SchemaUtils.create(
            ChatsTable,
            ChatMembersTable,
            MessagesTable
        )
    }
}