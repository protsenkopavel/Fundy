FROM gradle:8.14.3-jdk-21-and-24-alpine AS build
WORKDIR /home/gradle/project

COPY --chown=gradle:gradle build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon --build-cache

COPY --chown=gradle:gradle src ./src
RUN gradle clean bootJar -x test --no-daemon --build-cache

FROM openjdk:24-jdk-slim AS layer-extractor
WORKDIR application
COPY --from=build /home/gradle/project/build/libs/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM openjdk:24-jdk-slim
WORKDIR application
COPY --from=layer-extractor application/dependencies/ ./
COPY --from=layer-extractor application/snapshot-dependencies/ ./
COPY --from=layer-extractor application/spring-boot-loader/ ./
COPY --from=layer-extractor application/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]