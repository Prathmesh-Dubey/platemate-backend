# ---------- build stage ----------
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Download dependencies first so this layer is cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- run stage ----------
FROM eclipse-temurin:21-jre

WORKDIR /app

# Do not run as root
RUN useradd --system --create-home --uid 10001 appuser \
    && mkdir -p /app/uploads \
    && chown -R appuser /app

COPY --from=build /app/target/*.jar app.jar

USER appuser

# Memory friendly defaults for small instances (Render Free has 512 MB). Override with the JAVA_OPTS env var.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k"

# Render sets PORT and the app reads it (server.port=${PORT:8080}); 8080 is only the local default
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
