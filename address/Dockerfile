# Build from the repo root: docker build -f address/Dockerfile -t address-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY profile/pom.xml profile/pom.xml
COPY address/pom.xml address/pom.xml
COPY order/pom.xml order/pom.xml
COPY favorites/pom.xml favorites/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY address/src address/src
RUN mvn -q -pl address -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/address/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
