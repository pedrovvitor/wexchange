FROM openjdk:17-jdk-alpine

LABEL maintainer="pedrojppb@gmail.com"

ARG JAR_FILE=build/libs/wexchange-1.0-SNAPSHOT.jar

COPY ${JAR_FILE} wexchange.jar

ENTRYPOINT ["java", "-jar", "wexchange.jar"]
