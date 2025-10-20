# FILE: services/gateway/Dockerfile

# --- Этап 1: Сборка ---
# Используем образ Gradle с JDK 17
FROM gradle:8.7-jdk17 AS build

# Устанавливаем рабочую директорию внутри контейнера сборки
WORKDIR /home/gradle/project

# Копируем ВЕСЬ контент монорепозитория (из корня проекта, где docker-compose.yml)
# Это нужно, чтобы Gradle мог разрешить зависимости между модулями (например, :proto)
COPY . .

# Запускаем Gradle-задачу shadowJar ТОЛЬКО для модуля gateway
# Gradle сам найдет нужные зависимости (например, :proto)
RUN gradle :services:gateway:shadowJar

# --- Этап 2: Запуск ---
# Используем минимальный образ OpenJDK 17
FROM openjdk:17-jdk-slim

# Устанавливаем рабочую директорию внутри финального контейнера
WORKDIR /app

# Копируем собранный "fat" JAR (со всеми зависимостями) из этапа сборки
# Путь к JAR включает имя модуля: services/gateway/build/libs/gateway-...-all.jar
COPY --from=build /home/gradle/project/services/gateway/build/libs/gateway-1.0.0-all.jar app.jar

# Открываем порт 8080, который слушает Ktor
EXPOSE 8080

# Команда для запуска Ktor-приложения при старте контейнера
CMD ["java", "-jar", "app.jar"]