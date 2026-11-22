# syntax=docker/dockerfile:1
# Web app image: build with Maven, run on a slim Java 17 runtime.
#   docker compose up --build     (see docker-compose.yml)

FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
COPY core core
COPY desktop/pom.xml desktop/
COPY web web
# Cache Maven's downloads between builds
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl web -am package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd --system --create-home --uid 10001 app
WORKDIR /home/app
COPY --from=build /src/web/target/stock-trading-web.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
