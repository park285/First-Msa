# Build stage
FROM maven:3.8-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage  
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY --from=build /app/target/auth-service-0.0.1-SNAPSHOT.jar app.jar

# Change ownership and switch to a non-root user
RUN chown nobody:nogroup app.jar
USER nobody

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
