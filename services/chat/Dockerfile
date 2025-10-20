# Этап 1: Сборка
FROM gradle:8.7-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .
# Собираем fat jar (jar со всеми зависимостями)
RUN gradle shadowJar

# Этап 2: Запуск
FROM openjdk:17-jdk-slim
WORKDIR /app
# Копируем только собранный JAR
COPY --from=build /home/gradle/project/build/libs/chat-service-1.0.0-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]