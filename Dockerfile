# Этап 1: Сборка
FROM gradle:8.7-jdk17 AS build
WORKDIR /home/gradle/project
COPY . .
RUN gradle build

# Этап 2: Запуск
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /home/gradle/project/build/libs/mess-backend-1.0.0.jar app.jar
# Копируем все зависимости
COPY --from=build /home/gradle/project/build/distributions/mess-backend-1.0.0/lib /app/lib
RUN mkdir /app/data
EXPOSE 8080
# Указываем classpath для запуска
CMD ["java", "-cp", "app.jar:lib/*", "org.mess.backend.ApplicationKt"]