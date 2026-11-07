# Stage 1: Build Vue Frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend
COPY frontend/package*.json ./
RUN npm install
COPY frontend ./
RUN npm run build

# Stage 2: Build Spring Boot Backend with built Frontend
FROM maven:3.9-eclipse-temurin-17-alpine AS backend-builder
WORKDIR /app/backend
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=frontend-builder /app/frontend/dist ./src/main/resources/static
RUN mvn package -DskipTests -B

# Stage 3: Production Runtime
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install Python & system audio dependencies for Librosa Audio Worker
RUN apk add --no-cache python3 py3-pip ffmpeg libsndfile
RUN pip install --no-cache-dir --break-system-packages numpy scipy soundfile librosa

COPY --from=backend-builder /app/backend/target/*.jar app.jar
COPY backend/python ./backend/python
RUN mkdir -p /app/data /app/uploads/audio
ENV PORT=8080
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
