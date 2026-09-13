# ---- Build stage ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

# Copy Gradle wrapper and build config first for better layer caching
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Copy the committed OpenAPI specs — openApiGenerate (a compileJava dependency)
# reads specs/fantasy-db-service-openapi.yaml to generate the db-service client.
COPY specs ./specs

# Copy sources and build the executable boot jar (skip tests; CI already runs them)
COPY src ./src
RUN ./gradlew bootJar --no-daemon

# Unpack the boot jar so the runtime loads classes from plain jars on the classpath. Run as a
# nested jar, class loading goes through a lock in Spring Boot's NestedJarFile, and on staging's
# two cores that deadlocked both virtual-thread carriers: the BFF stopped answering everything,
# its health check included, and stayed that way until it was replaced (2026-09-11).
RUN cp build/libs/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extracted

# ---- Runtime stage ----
FROM eclipse-temurin:25-jre

# Coolify runs its health check with curl INSIDE the container, and the JRE image ships neither
# curl nor wget — so a container that started fine gets judged unhealthy and the deploy rolls
# back. Measured on 2026-08-09; see fantasy-workspace#2.
#
# fontconfig and a font are for the share card: it is drawn with Java2D, which needs real fonts
# on disk. The JRE image ships none, and without them every glyph renders as a box rather than
# failing loudly — so it would only surface when someone looked at a link preview.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fontconfig fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=build /app/extracted/ ./

# Render injects PORT; the app reads it via server.port=${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
