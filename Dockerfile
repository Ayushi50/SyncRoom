# Build stage: Node 22 for the web app, JDK 21 for the API
FROM eclipse-temurin:21-jdk AS build
RUN apt-get update && apt-get install -y curl ca-certificates \
 && curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
 && apt-get install -y nodejs \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY . .
RUN ./build.sh

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/rooms-0.1.0.jar app.jar
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
