# Build stage
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY gradlew build.gradle.kts settings.gradle.kts /app/
COPY gradle /app/gradle
COPY src /app/src

RUN chmod +x /app/gradlew \
  && /app/gradlew --no-daemon bootJar

# Runtime stage
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /app/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
