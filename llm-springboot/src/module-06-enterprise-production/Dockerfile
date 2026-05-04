FROM registry.access.redhat.com/ubi9/openjdk-21:latest

# Set working directory
WORKDIR /app

# Copy the JAR file
COPY target/*.jar app.jar

# Expose application port
EXPOSE 8086

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
