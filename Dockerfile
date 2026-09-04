# syntax=docker/dockerfile:1

# ---- build stage -----------------------------------------------------------
# The wrapper, not a global Gradle install: the version and checksum that get
# used are exactly the ones committed to this repository.
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /workspace

# Copied ahead of the source tree so dependency resolution is cached across
# source-only changes; only build.gradle/settings.gradle changing invalidates it.
COPY gradlew build.gradle settings.gradle lombok.config ./
COPY gradle ./gradle
RUN ./gradlew --version --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon

# ---- runtime stage ----------------------------------------------------------
# A JRE, not a JDK: nothing here compiles anything.
FROM eclipse-temurin:17-jre-alpine

RUN addgroup -S wexchange && adduser -S wexchange -G wexchange
WORKDIR /opt/app
COPY --from=builder --chown=wexchange:wexchange /workspace/build/libs/*.jar application.jar
USER wexchange

# The application activates no profile of its own. Choose one explicitly here,
# and override it per environment with SPRING_PROFILES_ACTIVE.
ENV SPRING_PROFILES_ACTIVE=production

EXPOSE 8080

# Exec form, deliberately: as PID 1 this receives SIGTERM directly rather than
# a shell wrapping it, which is what lets server.shutdown=graceful (see
# application.yml) actually run instead of the JVM being hard-killed after the
# container runtime's grace period.
ENTRYPOINT ["java", "-jar", "/opt/app/application.jar"]
