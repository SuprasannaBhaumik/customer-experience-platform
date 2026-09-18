# Build from the repo root: docker build -f order/Dockerfile -t order-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY profile/pom.xml profile/pom.xml
COPY address/pom.xml address/pom.xml
COPY order/pom.xml order/pom.xml
COPY favorites/pom.xml favorites/pom.xml
COPY gateway/pom.xml gateway/pom.xml
COPY order/src order/src
RUN mvn -q -pl order -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/order/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
