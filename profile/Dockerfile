# Build from the repo root: docker build -f profile/Dockerfile -t profile-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY profile/pom.xml profile/pom.xml
COPY address/pom.xml address/pom.xml
COPY order/pom.xml order/pom.xml
COPY favorites/pom.xml favorites/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY profile/src profile/src
RUN mvn -q -pl profile -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/profile/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
