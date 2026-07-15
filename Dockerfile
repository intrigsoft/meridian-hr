# syntax=docker/dockerfile:1
# ---- Build stage: compile the Spring Boot fat jar with the pinned wrapper ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Warm the dependency cache before copying sources so edits don't re-download.
COPY gradlew settings.gradle.kts build.gradle.kts gradle.properties ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies >/dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew --no-daemon clean bootJar

# ---- Run stage: slim JRE, non-root, binds the platform-provided PORT ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# temurin already ships a UID 1000 user, so don't pin the UID — let the system pick one.
RUN useradd --system --create-home --shell /usr/sbin/nologin meridian
COPY --from=build /app/build/libs/*.jar app.jar
USER meridian

# App reads server.port from ${PORT} (Railway/containers inject it); 3020 is the local default.
ENV PORT=3020
EXPOSE 3020
ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
