# syntax=docker/dockerfile:1.7

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package \
    && cp target/social-publisher-*.jar app.jar

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app

# ffmpeg: required by VideoStitchService to concatenate per-photo AI-generated clips into one
# video before publishing. Not optional — there is no pure-Java fallback for this.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ffmpeg \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system --gid 10001 socialpub \
    && useradd --system --uid 10001 --gid socialpub --create-home --shell /usr/sbin/nologin socialpub \
    && mkdir -p /app/logs \
    && chown -R socialpub:socialpub /app
USER socialpub

COPY --from=build /workspace/app.jar app.jar

EXPOSE 8090
ENV JAVA_OPTS=""
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -f http://localhost:8090/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
