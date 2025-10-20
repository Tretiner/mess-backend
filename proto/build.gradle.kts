import com.google.protobuf.gradle.id

plugins {
    id("com.google.protobuf") version "0.9.4"
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    // Зависимости, необходимые для gRPC
    implementation("io.grpc:grpc-protobuf:1.64.0")
    implementation("io.grpc:grpc-stub:1.64.0")
    implementation("com.google.protobuf:protobuf-kotlin:3.25.3")
    // Для корутин в gRPC
    implementation("io.grpc:grpc-kotlin-stub:1.4.1")
}

// Настройка плагина
protobuf {
    protoc {
        // Где брать бинарник компилятора
        artifact = "com.google.protobuf:protoc:3.25.3"
    }
    plugins {
        // Плагин для генерации "чистого" Java/Kotlin
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.64.0"
        }
        // Плагин для генерации Kotlin-корутин
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:1.4.1:jdk8@jar"
        }
    }
    // Говорим плагину, что генерировать
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
        }
    }
}