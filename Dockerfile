FROM openjdk:17-jdk-alpine

LABEL maintainer="pedrojppb@gmail.com"

ARG JAR_FILE=target/*.jar

COPY ${JAR_FILE} app.jar

ENTRYPOINT ["java","-jar","/app.jar"]
