FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
COPY ratelimiter-core/pom.xml ratelimiter-core/
COPY ratelimiter-redis/pom.xml ratelimiter-redis/
COPY gateway/pom.xml gateway/
RUN mvn -B -q -pl gateway -am dependency:go-offline
COPY ratelimiter-core ratelimiter-core
COPY ratelimiter-redis ratelimiter-redis
COPY gateway gateway
RUN mvn -B -q -pl gateway -am package -DskipTests

FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home gateway
USER gateway
COPY --from=build /build/gateway/target/gateway-*-SNAPSHOT.jar /app/gateway.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/gateway.jar"]
