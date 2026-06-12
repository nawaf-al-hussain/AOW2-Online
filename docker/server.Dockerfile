# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
COPY aow2-common/ aow2-common/
COPY aow2-core/ aow2-core/
COPY aow2-server/ aow2-server/
COPY aow2-modding/ aow2-modding/

RUN chmod +x gradlew
RUN ./gradlew :aow2-server:bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

COPY --from=build /app/aow2-server/build/libs/*.jar app.jar

EXPOSE 8080 8443

ENTRYPOINT ["java", "--enable-preview", "-jar", "app.jar"]
