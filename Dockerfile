# Image for the Hashtag Local backend on Cloud Run.
# Built and deployed by .github/workflows/deploy.yml.

# ---- build: the jar (tests are run by the workflow's test job) ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /src

# Wrapper and build scripts first, so the dependency download is cached
# until the build files change.
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src src
RUN ./gradlew --no-daemon bootJar -x test \
    && cp "$(ls build/libs/*.jar | grep -v -- '-plain' | head -n 1)" /app.jar

# ---- runtime ----
FROM eclipse-temurin:25-jre
WORKDIR /app

# Run as an unprivileged user.
RUN useradd --system --uid 10001 app
COPY --from=build --chown=app /app.jar app.jar
USER app

# Cloud Run sets PORT (8080 by default); Spring Boot's server.port default matches.
ENV PORT=8080
EXPOSE 8080

# Size the heap from the container's memory limit.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
