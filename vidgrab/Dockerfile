# Stage 1: project build karna
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

# Stage 2: app chalana
FROM eclipse-temurin:17-jre
RUN apt-get update \
 && apt-get install -y --no-install-recommends python3 ffmpeg curl unzip ca-certificates \
 && rm -rf /var/lib/apt/lists/* \
 && curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp \
 && chmod a+rx /usr/local/bin/yt-dlp \
 && curl -fsSL https://deno.land/install.sh | DENO_INSTALL=/usr/local sh
COPY --from=build /app/target/*.jar /app/app.jar
ENV YTDLP_PATH=/usr/local/bin/yt-dlp
CMD ["java", "-jar", "/app/app.jar"]
