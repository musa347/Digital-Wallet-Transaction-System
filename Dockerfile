# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /build

# Copy the entire project
COPY . .

# Build both services
RUN cd wallet-service && mvn clean package -DskipTests
RUN cd history-service && mvn clean package -DskipTests

# Final stage
FROM eclipse-temurin:17-jdk-alpine

# Create directories for both services
WORKDIR /app
RUN mkdir -p /app/wallet /app/history

# Copy built JARs
COPY --from=build /build/wallet-service/target/*.jar /app/wallet/wallet-service.jar
COPY --from=build /build/history-service/target/*.jar /app/history/history-service.jar

# Create startup script using a simpler approach
RUN echo '#!/bin/sh' > /app/start-services.sh && \
    echo 'java -Dserver.port=8080 -jar /app/wallet/wallet-service.jar &' >> /app/start-services.sh && \
    echo 'WALLET_PID=$!' >> /app/start-services.sh && \
    echo 'java -Dserver.port=8081 -jar /app/history/history-service.jar &' >> /app/start-services.sh && \
    echo 'HISTORY_PID=$!' >> /app/start-services.sh && \
    echo 'wait $WALLET_PID $HISTORY_PID' >> /app/start-services.sh && \
    echo 'exit $?' >> /app/start-services.sh && \
    chmod +x /app/start-services.sh

# Expose both ports
EXPOSE 8080 8081

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["/app/start-services.sh"]
