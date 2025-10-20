# Этап 1: Сборка
FROM gradle:8.7-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .

# Запускаем сборку ТОЛЬКО для нового модуля :services:user
RUN gradle :services:user:shadowJar # <-- ИЗМЕНЕНИЕ

# Этап 2: Создание финального образа
FROM openjdk:17-jdk-slim
WORKDIR /app

# Имя JAR-файла теперь "user-1.0.0-all.jar"
COPY --from=build /home/gradle/project/services/user/build/libs/user-1.0.0-all.jar app.jar

# Запускаем сервис
CMD ["java", "-jar", "app.jar"]