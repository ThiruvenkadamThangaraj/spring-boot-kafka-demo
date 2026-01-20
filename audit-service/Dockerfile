FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/audit-service-0.0.1-SNAPSHOT.jar audit-service.jar

EXPOSE 8086

ENTRYPOINT ["java", "-jar", "audit-service.jar"]
