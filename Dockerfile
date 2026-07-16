# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-25 AS builder
WORKDIR /workspace

# Dependencies are cached separately from application sources.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp clean package -DskipTests && \
    cp target/*.jar /workspace/application.jar

FROM eclipse-temurin:25-jre

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport" \
    SERVER_PORT=8080

WORKDIR /app

RUN groupadd --system --gid 10001 app && \
    useradd --system --uid 10001 --gid app --home-dir /app --shell /usr/sbin/nologin app && \
    mkdir -p /app/data && \
    chown -R app:app /app

COPY --from=builder --chown=app:app /workspace/application.jar /app/application.jar

USER app
EXPOSE 8080
VOLUME ["/app/data"]

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/application.jar"]
