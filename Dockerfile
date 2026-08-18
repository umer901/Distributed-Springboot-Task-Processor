FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -Dmaven.repo.local=/root/.m2/repository dependency:go-offline
COPY src ./src
RUN mvn -B -Dmaven.repo.local=/root/.m2/repository -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/target/distributed-task-processor-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
