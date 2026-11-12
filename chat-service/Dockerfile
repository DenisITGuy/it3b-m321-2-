FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Root POM und ALLE Modul-POMs kopieren (damit Maven die Struktur validieren kann)
COPY pom.xml .
COPY chat-service/pom.xml ./chat-service/
COPY batch-writer/pom.xml ./batch-writer/

# 2. Nur die Sources des zu bauenden Moduls kopieren
COPY chat-service/src ./chat-service/src

# 3. Nur das chat-service Modul bauen (inkl. Parent)
RUN mvn -B package -DskipTests -pl chat-service -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/chat-service/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]