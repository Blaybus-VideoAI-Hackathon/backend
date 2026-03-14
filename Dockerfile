FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY . .

RUN chmod +x gradlew
RUN ./gradlew bootJar

EXPOSE 8080

CMD ["sh", "-c", "java -jar build/libs/*SNAPSHOT.jar --server.port=${PORT:-8080}"]
