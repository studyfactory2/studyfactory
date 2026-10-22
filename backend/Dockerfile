FROM eclipse-temurin:21-jre

# Only images without the legacy drink importer are safe rollback targets.
LABEL io.studyfactory.beverage-state="canonical-v1"

WORKDIR /app

COPY app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
