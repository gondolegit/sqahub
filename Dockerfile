# --- Stage 1: Build ---
# Pakai image Maven+JDK cuma untuk build, tidak ikut ke image final (multi-stage)
# supaya image production tidak membawa toolchain build yang tidak perlu.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml dulu & download dependency SEBELUM copy source code, supaya layer ini
# di-cache Docker selama pom.xml tidak berubah (build ulang jadi jauh lebih cepat).
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# --- Stage 2: Runtime ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Jalan sebagai user non-root, bukan root default container - praktik keamanan standar.
RUN addgroup -S sqahub && adduser -S sqahub -G sqahub
USER sqahub

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8080

# Healthcheck memakai /actuator/health yang memang publik (lihat SecurityConfiguration).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
