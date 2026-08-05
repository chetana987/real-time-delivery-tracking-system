# syntax=docker/dockerfile:1

# ============================================================
# Stage 1: build - Maven + JDK 21 compiles the Spring Boot jar
# ============================================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copy the POM first and resolve dependencies so this layer is cached
# and only re-runs when pom.xml changes.
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy sources and package. -DskipTests compiles tests but does not run
# them: OrderConcurrencyTest needs a live MySQL instance, which is not
# available during image builds. Tests run separately in CI.
COPY src ./src
RUN mvn -B -DskipTests package

# ============================================================
# Stage 2: runtime - minimal JRE image with only the jar
# ============================================================
FROM eclipse-temurin:21-jre-jammy

# curl is used by the docker-compose healthcheck against /api/health.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Run as a non-root user (defence in depth).
RUN groupadd --system spring \
    && useradd --system --gid spring --create-home --home-dir /app spring

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

USER spring

# Optional JVM tuning, e.g. docker run -e JAVA_OPTS="-Xmx512m"
ENV JAVA_OPTS=""
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
