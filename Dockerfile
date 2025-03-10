# Use an OpenJDK 21 base image
FROM openjdk:21-slim as builder

# Set environment variables
ENV MAVEN_HOME=/usr/share/maven
ENV MAVEN_CONFIG=/root/.m2

# Install Maven
RUN apt-get update && apt-get install -y maven && apt-get clean

# Set the working directory
WORKDIR /app

# Copy the Maven POM file to leverage Docker cache
COPY pom.xml .

# Download dependencies (this will be cached)
RUN mvn dependency:go-offline

# Copy the entire application source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Final stage - minimal runtime image
FROM openjdk:21-slim

# Set the working directory
WORKDIR /app

# Copy the jar file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Expose port 8080
EXPOSE 8080

# Run the application
CMD ["java", "-jar", "app.jar"]
