# ---- Build stage: compile and package the application ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Download dependencies first, so this layer is reused when only the source changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
# Tests need no external services and run before every commit; skipping them here
# keeps the build within the memory limits of small hosting plans.
RUN ./mvnw -q -B package -DskipTests

# ---- Run stage: a small image with only the Java runtime and the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --no-create-home appuser
COPY --from=build /app/target/alumni-searcher-*.jar app.jar
USER appuser

EXPOSE 8080
# Size the heap from the container's memory limit instead of the host's.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
