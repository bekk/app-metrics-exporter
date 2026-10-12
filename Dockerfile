# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY app/pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY app/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -DskipTests package \
    && cp target/*.jar /build/app.jar

FROM eclipse-temurin:21-jre-alpine AS runtime
# Samme uid/gid som runAsUser/runAsGroup i k8s/deployment.yaml.
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=10001:10001 /build/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
