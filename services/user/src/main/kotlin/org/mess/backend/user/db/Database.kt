package org.mess.backend.user.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update // Для автообновления updatedAt

// Конфиг остается прежним
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
        // Exposed создаст или обновит схему
        SchemaUtils.create(UserProfilesTable)

        // Дополнительно: можно создать триггер для автообновления `updatedAt` в Postgres
        // exec("CREATE OR REPLACE FUNCTION update_updated_at_column()\n" +
        //      "RETURNS TRIGGER AS $$\n" +
        //      "BEGIN\n" +
        //      "   NEW.updated_at = now();\n" +
        //      "   RETURN NEW;\n" +
        //      "END;\n" +
        //      "$$ language 'plpgsql';")
        // exec("DROP TRIGGER IF EXISTS update_user_profiles_updated_at ON t_user_profiles;")
        // exec("CREATE TRIGGER update_user_profiles_updated_at\n" +
        //      "BEFORE UPDATE ON t_user_profiles\n" +
        //      "FOR EACH ROW\n" +
        //      "EXECUTE FUNCTION update_updated_at_column();")
    }
}