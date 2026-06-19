# Build stage - compile Spring Boot application
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

# Copy Gradle wrapper and build files first (for layer caching)
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew

# Copy source code for all modules
COPY aow2-common/ aow2-common/
COPY aow2-core/ aow2-core/
COPY aow2-server/ aow2-server/
COPY aow2-modding/ aow2-modding/

# Build the server JAR (skip tests for faster builds)
RUN ./gradlew :aow2-server:bootJar --no-daemon -x test

# Runtime stage - minimal JRE image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S aow2 && adduser -S aow2 -G aow2

# Copy the built JAR from the build stage
COPY --from=build /app/aow2-server/build/libs/*.jar app.jar

# Create directories for persistent data
RUN mkdir -p /app/replays /app/uploads/maps && chown -R aow2:aow2 /app

# Switch to non-root user
USER aow2

# Expose HTTP and HTTPS ports
EXPOSE 8080 8443

# Health check endpoint
HEALTHCHECK --interval=15s --timeout=10s --retries=3 --start-period=30s \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# FIX (P3-K1): Validate JWT secret is set and not the dev default.
# Prevents production deployments from running with the insecure default key.
# Users must set AOW2_JWT_SECRET environment variable to a real secret (>= 32 chars).
ENV AOW2_JWT_SECRET_CHECK=REQUIRED

# JVM options: G1GC for low latency, preview features for Java 21
ENTRYPOINT ["sh", "-c", "if [ -z \"$AOW2_JWT_SECRET\" ] || [ \"$AOW2_JWT_SECRET\" = \"aow2-dev-only-secret-key-that-is-at-least-32-bytes-long-for-hmac\" ]; then echo 'ERROR: AOW2_JWT_SECRET must be set to a secure secret (>= 32 chars). Do not use the default dev key in production.' >&2; exit 1; fi; java ${JAVA_OPTS:--XX:+UseG1GC --enable-preview} -jar app.jar"]
