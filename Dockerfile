# build stage
FROM gradle:8.4-jdk17-alpine AS builder

WORKDIR /usr/app/

COPY . .

RUN gradle bootJar

# build runtime
FROM openjdk:17-jdk-alpine

COPY --from=builder /usr/app/build/libs/*.jar /opt/app/application.jar

# The application activates no profile of its own. Choose one explicitly here,
# and override it per environment with SPRING_PROFILES_ACTIVE.
ENV SPRING_PROFILES_ACTIVE=production

CMD java -jar /opt/app/application.jar
