# ── Stage 1: Build ────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Dependencies pehle cache karo (layer caching ke liye)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Source code copy karo aur build karo
COPY src ./src
RUN mvn clean package -DskipTests -B

# ── Stage 2: Runtime ───────────────────────────────────────────
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# FFmpeg install karo (HLS video processing ke liye zaroori)
# curl bhi chahiye — docker healthcheck usse hit karta hai
RUN apt-get update && \
    apt-get install -y --no-install-recommends ffmpeg curl && \
    rm -rf /var/lib/apt/lists/*

# Build stage se jar copy karo
COPY --from=build /app/target/*.jar app.jar

# Video storage directories (volumes se mount hongi)
RUN mkdir -p /app/videos /app/videos_hls

EXPOSE 2911

ENTRYPOINT ["java", "-jar", "app.jar"]
