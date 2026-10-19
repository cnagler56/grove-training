# Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy pom.xml first (caches dependencies if code changes but pom doesn't)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Don't run as root
RUN addgroup -S spring && adduser -S spring -G spring
USER spring

COPY --from=build /app/target/*.jar app.jar

# Small heap keeps Railway's usage-based RAM bill low; this app holds no video data.
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"

EXPOSE 8084

ENTRYPOINT ["java", "-jar", "app.jar"]
