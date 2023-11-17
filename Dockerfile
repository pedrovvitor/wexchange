# build stage
FROM gradle:8.4-jdk17-alpine AS builder

WORKDIR /usr/app/

COPY . .

RUN gradle bootJar

# build runtime
FROM openjdk:17-jdk-alpine

COPY --from=builder /usr/app/build/libs/*.jar /opt/app/application.jar

CMD java -jar /opt/app/application.jar