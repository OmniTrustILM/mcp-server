# Stage 1: Build
FROM eclipse-temurin:27-jdk AS build

WORKDIR /build

COPY pom.xml .
COPY src src

RUN --mount=type=cache,target=/root/.m2 \
    apt-get update && apt-get install -y --no-install-recommends maven && \
    mvn -B -U package -DskipTests

# Stage 2: Optimize - create custom JRE for the Alpine runtime
FROM eclipse-temurin:27-jdk-alpine AS optimize

COPY --from=build /build/target/*.jar /app/app.jar

RUN jar xf /app/app.jar && \
    jdeps --ignore-missing-deps \
        --print-module-deps \
        --multi-release 21 \
        --class-path 'BOOT-INF/lib/*' \
        /app/app.jar > /app/deps.info && \
    jlink \
        --add-modules $(cat /app/deps.info),jdk.crypto.ec \
        --strip-debug \
        --compress zip-6 \
        --no-header-files \
        --no-man-pages \
        --output /custom-jre

# Stage 3: Package
FROM alpine:3.24

LABEL org.opencontainers.image.authors="ILM <support@otilm.com>"

# VERSION changes per release, so a tag build never reuses a cached, outdated upgrade layer
ARG VERSION
RUN apk upgrade --no-cache && \
    addgroup -g 10001 ilm && \
    adduser -u 10001 -G ilm -s /bin/sh -D ilm

COPY --from=optimize /custom-jre /opt/java
COPY docker /
COPY --from=build /build/target/*.jar /opt/ilm/app.jar

ENV PATH="/opt/java/bin:${PATH}"
ENV JAVA_OPTS=""

USER ilm
WORKDIR /opt/ilm

EXPOSE 8080

ENTRYPOINT ["/opt/ilm/entry.sh"]
