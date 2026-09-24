# syntax=docker/dockerfile:1
#
# Multi-stage JVM image for the palais-divin backend.
# Build from the REPO ROOT:  docker build -f src/main/docker/Dockerfile -t <tag> .

# ---- build ---------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/

# The ~/.m2 cache mount keeps dependency downloads across builds; no
# `dependency:go-offline` pre-pass — it resolves an incomplete set for Boot
# projects and fails the build more often than it saves time.
# `package`, not `verify`: spotless:check and failsafe are bound to verify and
# belong in CI, not in the image build.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

# Split the fat jar into layers so a code-only change re-pushes ~a few MB
# instead of the whole ~100MB jar. Boot 4 spells this `-Djarmode=tools`
# (the old `-Djarmode=layertools` is gone).
RUN cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

# ---- runtime -------------------------------------------------------------
# No java25-debian12 variant exists — Distroless ships Java 25 on debian13 only.
# Base image: no shell, no package manager, runs as uid 65532. That also means
# no container HEALTHCHECK is possible (nothing to exec); liveness is observed
# from outside via GET /actuator/health, which SecurityConfig leaves permitAll.
FROM gcr.io/distroless/java25-debian13:nonroot
WORKDIR /app

COPY --from=build --chown=65532:65532 /build/extracted/dependencies/ ./
COPY --from=build --chown=65532:65532 /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=65532:65532 /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=65532:65532 /build/extracted/application/ ./

# Heap follows the container limit rather than a hardcoded -Xmx, so the compose
# `mem_limit` is the single place memory is tuned. Override the whole variable
# at run time to add flags.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8080

# The base image's entrypoint is ["/usr/bin/java", "-jar"], which cannot launch
# an extracted layout — override it with the loader's launcher class.
ENTRYPOINT ["/usr/bin/java", "org.springframework.boot.loader.launch.JarLauncher"]
