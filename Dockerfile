# --- build stage ---
FROM gradle:8.14-jdk21 AS build
WORKDIR /src
COPY . .
RUN gradle bootJar --no-daemon -x test

# --- run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Xmx384m", "-jar", "app.jar"]
