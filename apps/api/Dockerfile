# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --home-dir /app borrowbox \
 && mkdir -p /app/uploads \
 && chown -R borrowbox /app
WORKDIR /app
COPY --from=build /src/target/borrowbox-api.jar app.jar
USER borrowbox
ENV SPRING_PROFILES_ACTIVE=prod \
    UPLOAD_DIR=/app/uploads \
    PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-Djava.awt.headless=true", "-XX:+UseSerialGC", "-Xss512k", "-XX:MaxRAMPercentage=70", "-jar", "/app/app.jar"]
