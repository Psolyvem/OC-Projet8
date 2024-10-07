FROM eclipse-temurin:19
WORKDIR /app
COPY target/tourguide-0.0.1-SNAPSHOT.jar /app/tourguide-0.0.1-SNAPSHOT.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/tourguide-0.0.1-SNAPSHOT.jar"]