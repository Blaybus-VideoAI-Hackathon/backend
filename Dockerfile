# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy settings.gradle first (required for project identification)
COPY settings.gradle .

# Copy gradle wrapper and gradle files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .

# Make gradlew executable and download dependencies
RUN chmod +x gradlew

# Copy source code (all src directory)
COPY src src

# Build the application (with explicit main class)
RUN ./gradlew clean bootJar -x test --info

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the jar from build stage
COPY --from=build /app/build/libs/hdb-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
