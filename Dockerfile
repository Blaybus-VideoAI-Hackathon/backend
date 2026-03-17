# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Copy gradle wrapper and gradle files first for better caching
COPY gradlew .
COPY gradle gradle
COPY build.gradle .

# Make gradlew executable and download dependencies
RUN chmod +x gradlew
RUN ./gradlew dependencies --configuration runtimeClasspath || true

# Copy source code
COPY src src

# Build the application
RUN ./gradlew bootJar -x test

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

# Copy the jar from build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
CMD ["sh", "-c", "java -jar app.jar --server.port=${PORT:-8080}"]
