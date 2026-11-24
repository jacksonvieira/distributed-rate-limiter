FROM maven:3.9-eclipse-temurin-26 AS build
ARG MODULE=gateway
WORKDIR /build
COPY pom.xml .
COPY ratelimiter-core/pom.xml ratelimiter-core/
COPY ratelimiter-redis/pom.xml ratelimiter-redis/
COPY gateway/pom.xml gateway/
COPY admin-api/pom.xml admin-api/
COPY benchmarks/pom.xml benchmarks/
RUN mvn -B -q -pl ${MODULE} -am dependency:go-offline
COPY ratelimiter-core ratelimiter-core
COPY ratelimiter-redis ratelimiter-redis
COPY gateway gateway
COPY admin-api admin-api
RUN mvn -B -q -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:25-jre
ARG MODULE=gateway
RUN useradd --system --no-create-home app
USER app
COPY --from=build /build/${MODULE}/target/${MODULE}-*-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
