# Этап 1: Сборка
FROM gradle:8.7-jdk17 AS build
WORKDIR /home/gradle/project
# Копируем весь монорепозиторий
COPY . .

# Запускаем сборку ТОЛЬКО для нового модуля :services:auth
RUN gradle :services:auth:shadowJar # <-- ИЗМЕНЕНИЕ

# Этап 2: Создание финального образа
FROM openjdk:17-jdk-slim
WORKDIR /app

# Копируем "fat" JAR из этапа сборки
# Имя JAR-файла теперь "auth-1.0.0-all.jar"
COPY --from=build /home/gradle/project/services/auth/build/libs/auth-1.0.0-all.jar app.jar

# Запускаем сервис
CMD ["java", "-jar", "app.jar"]