FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# 1. Root POM und ALLE Modul-POMs kopieren (damit Maven die Struktur validieren kann)
COPY pom.xml .
COPY backend/pom.xml ./backend/
COPY batch-writer/pom.xml ./batch-writer/

# 2. Nur die Sources des zu bauenden Moduls kopieren
COPY backend/src ./backend/src

# 3. Nur das backend Modul bauen (inkl. Parent)
RUN mvn -B package -DskipTests -pl backend -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/backend/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]