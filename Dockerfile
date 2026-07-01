# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Gradle wrapper + build config first → dependency layer cached across source changes
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x ./gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

# Source + build the executable boot jar (tests skipped for image build)
COPY src ./src
RUN ./gradlew --no-daemon clean bootJar -x test

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as non-root
RUN useradd -r -u 1001 spring

# Only the boot jar (bootJar task produces a single executable jar)
COPY --from=build /app/build/libs/*.jar app.jar

USER spring

# Railway injects $PORT at runtime; application.yml reads ${PORT:8080}. EXPOSE is documentation only.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
