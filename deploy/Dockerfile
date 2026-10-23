# Multi-stage build: Maven build with a warm dependency layer, then a minimal JRE runtime image
# running as a non-root user. The same image runs as API or worker depending on TALLYARD_ROLE.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY tallyard-app/pom.xml tallyard-app/pom.xml
COPY tallyard-simulator/pom.xml tallyard-simulator/pom.xml
RUN mvn -q -B -pl tallyard-app -am dependency:go-offline -DskipTests || true
COPY tallyard-app tallyard-app
COPY tallyard-simulator tallyard-simulator
RUN mvn -q -B -pl tallyard-app -am package -DskipTests -Dspotless.check.skip=true \
 && java -Djarmode=tools -jar tallyard-app/target/tallyard-app-*.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:21-jre-alpine AS runtime
RUN addgroup -S tallyard && adduser -S -G tallyard tallyard \
 && apk add --no-cache wget
WORKDIR /app
COPY --from=build /layers/dependencies/ ./
COPY --from=build /layers/spring-boot-loader/ ./
COPY --from=build /layers/snapshot-dependencies/ ./
COPY --from=build /layers/application/ ./
USER tallyard
EXPOSE 8080 9091
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
