#!/bin/bash
set -e

# Выполняем psql-команды от имени суперпользователя (postgres)
# Переменные $POSTGRES_USER и $POSTGRES_PASSWORD уже установлены
# docker-compose.yml (admin / admin_password)
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- Создаем 3 базы данных для наших 3-х сервисов
    CREATE DATABASE auth_db;
    CREATE DATABASE user_db;
    CREATE DATABASE chat_db;
EOSQL