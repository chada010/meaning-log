# --- Build stage ---
FROM maven:3.9-eclipse-temurin-17 AS build
ENV MAVEN_OPTS="-Xmx512m"
WORKDIR /build

COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests

COPY src src
RUN mvn -B package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build --chown=app:app /build/target/meaning-log-backend-*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java ${JAVA_OPTS:--Xmx384m -Xms256m} -jar app.jar"]
