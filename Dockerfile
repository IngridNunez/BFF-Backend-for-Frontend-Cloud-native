# ============================================
# Etapa 1: Descargar dependencias (cache)
# ============================================
FROM maven:3.9.14-eclipse-temurin-17-alpine AS deps
WORKDIR /app
COPY pom.xml ./
COPY .mvn .mvn
COPY mvnw ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

# ============================================
# Etapa 2: Compilar la aplicación
# ============================================
FROM deps AS build
COPY src ./src
RUN ./mvnw -B package -DskipTests

# ============================================
# Etapa 3: Imagen final liviana
# ============================================
FROM eclipse-temurin:17-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-Djava.net.preferIPv4Stack=true", "-jar", "app.jar"]
