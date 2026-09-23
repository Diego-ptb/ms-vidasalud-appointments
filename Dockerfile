# --- Etapa de compilacion -------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# El pom va primero y solo: asi Docker cachea las dependencias y no vuelve a
# descargarlas cuando cambia unicamente el codigo fuente.
COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests

# --- Imagen final ---------------------------------------------------------
# Solo el JRE: la imagen queda mucho mas liviana que con el JDK y Maven.
FROM eclipse-temurin:17-jre
WORKDIR /app

# Ejecutar como usuario sin privilegios: si alguien compromete el servicio,
# no obtiene root dentro del contenedor.
RUN groupadd --system spring && useradd --system --gid spring spring

COPY --from=build /build/target/*.jar app.jar
RUN chown spring:spring /app/app.jar

USER spring
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
