package org.mess.backend.plugins

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.*
import io.ktor.server.config.*
import org.ktorm.database.Database
import org.ktorm.logging.Slf4jLoggerAdapter

fun Application.configureDatabase(config: ApplicationConfig): Database {
    // 1. Читаем конфиг из application.conf
    val host = config.property("ktor.db.host").getString()
    val port = config.property("ktor.db.port").getString()
    val user = config.property("ktor.db.user").getString()
    val pass = config.property("ktor.db.password").getString()
    val name = config.property("ktor.db.name").getString()

    // 2. Настраиваем Hikari Connection Pool
    val hikariConfig = HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://$host:$port/$name"
        driverClassName = "org.postgresql.Driver"
        username = user
        password = pass
        maximumPoolSize = 10
        isAutoCommit = false // Ktorm управляет транзакциями
        transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        validate()
    }

    val dataSource = HikariDataSource(hikariConfig)

    // 3. Создаем экземпляр Ktorm Database
    val database = Database.connect(
        dataSource = dataSource,
        dialect = PostgreSqlDialect(),
        logger = Slf4jLoggerAdapter(log.name) // Используем логгер Ktor
    )

    // 4. Инициализация схемы (т.к. Ktorm не делает это автоматически)
    // В production лучше использовать Flyway или Liquibase
    database.useConnection { conn ->
        val sql = """
        -- Включаем расширение для генерации UUID
        CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

        CREATE TABLE IF NOT EXISTS t_users (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            username VARCHAR(50) NOT NULL UNIQUE,
            password_hash VARCHAR(255) NOT NULL,
            nickname VARCHAR(50) NOT NULL,
            avatar_url VARCHAR(255),
            created_at TIMESTAMP NOT NULL DEFAULT NOW()
        );
        
        CREATE TABLE IF NOT EXISTS t_chats (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            name VARCHAR(100),
            is_group BOOLEAN NOT NULL DEFAULT false,
            created_at TIMESTAMP NOT NULL DEFAULT NOW()
        );
        
        CREATE TABLE IF NOT EXISTS t_chat_members (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            chat_id UUID NOT NULL REFERENCES t_chats(id) ON DELETE CASCADE,
            user_id UUID NOT NULL REFERENCES t_users(id) ON DELETE CASCADE,
            joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
            -- Нельзя добавить одного юзера в чат дважды
            UNIQUE(chat_id, user_id) 
        );
        
        CREATE TABLE IF NOT EXISTS t_messages (
            id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
            chat_id UUID NOT NULL REFERENCES t_chats(id) ON DELETE CASCADE,
            sender_id UUID NOT NULL REFERENCES t_users(id) ON DELETE CASCADE,
            type VARCHAR(20) NOT NULL DEFAULT 'text',
            content TEXT NOT NULL,
            sent_at TIMESTAMP NOT NULL DEFAULT NOW()
        );
        """
        conn.createStatement().execute(sql)
    }

    log.info("Database initialized and connected.")
    return database
}