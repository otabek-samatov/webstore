# syntax=docker/dockerfile:1
# =============================================================================
# Shared multi-stage Dockerfile for ALL webstore services.
# Select the module with a build arg:  --build-arg SERVICE=order-service
# (docker-compose.yml passes it per service.)
# =============================================================================

# ---- Build stage: compile the selected module with the Gradle wrapper -------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY . .
# Windows checkouts may give gradlew CRLF endings; normalize before executing.
RUN sed -i 's/\r$//' gradlew && chmod +x gradlew

ARG SERVICE
# Cache mount keeps the Gradle distribution + dependency cache across builds
# of all 8 services, so only the first build pays the full download cost.
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew :${SERVICE}:bootJar -x test --no-daemon

# ---- Runtime stage: JRE only -------------------------------------------------
FROM eclipse-temurin:25-jre
# curl is used by the compose healthchecks (temurin images don't ship it).
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

ARG SERVICE
# Only bootJar ran in the build stage, so build/libs contains exactly one jar.
COPY --from=build /workspace/${SERVICE}/build/libs/*.jar /app.jar

ENTRYPOINT ["java", "-jar", "/app.jar"]
