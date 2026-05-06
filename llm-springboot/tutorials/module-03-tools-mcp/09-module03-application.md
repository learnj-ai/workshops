# Chapter: Module03Application - The Bootstrap Class

## Introduction: Starting Your AI System

Every Spring Boot application needs an entry point—a main class that bootstraps the entire system. **Module03Application** is that entry point. It's deceptively simple, but it triggers a cascade of initialization that brings your AI assistant to life.

This class is where Spring Boot's "convention over configuration" philosophy shines. With minimal code, you get a fully configured web server, dependency injection, and all the components you've built wired together automatically.

## Code Deep Dive

Let's examine the Module03Application class:

```java
package com.techcorp.assistant.module03;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Module 03: Tools and Model Context Protocol
 *
 * This module demonstrates:
 * - Database tools with @Tool annotations
 * - External API integration (Weather)
 * - MCP-compliant tool registration
 * - Tool orchestration with LLMs
 */
@SpringBootApplication
public class Module03Application {
    public static void main(String[] args) {
        SpringApplication.run(Module03Application.class, args);
    }
}
```

## The @SpringBootApplication Annotation

```java
@SpringBootApplication
public class Module03Application {
```

This single annotation is actually **three annotations in one**:

### 1. @Configuration
Marks this class as a source of bean definitions. While we don't define beans here (they're in MCPServerConfig), this enables Spring to scan for configuration classes.

### 2. @EnableAutoConfiguration
This is where the magic happens. Spring Boot automatically configures your application based on:
- **Dependencies on the classpath**: Sees `spring-boot-starter-web` → configures embedded Tomcat
- **Beans you define**: Sees ChatModel bean → knows you're using OpenAI
- **Properties you set**: Sees `server.port=8083` → runs on port 8083

**What gets auto-configured:**
- Embedded web server (Tomcat)
- JSON serialization (Jackson)
- Database connection pool (HikariCP)
- JDBC template
- Request/response handling
- Exception handling
- Logging framework
- And much more!

### 3. @ComponentScan
Scans the current package (`com.techcorp.assistant.module03`) and all sub-packages for Spring components:
- `@Service` (ToolOrchestrator)
- `@Component` (CustomerDataTool, WeatherTool)
- `@RestController` (AssistantController)
- `@Configuration` (MCPServerConfig)

**Component scanning finds and instantiates all your beans automatically.**

## The main Method

```java
public static void main(String[] args) {
    SpringApplication.run(Module03Application.class, args);
}
```

This is the entry point of your Java application. When you run:
```bash
mvn spring-boot:run
```

Or:
```bash
java -jar module-03-tools-mcp.jar
```

This main method executes and:

1. **Creates a SpringApplication instance**
2. **Loads configuration** from application.properties
3. **Scans for components** in the package hierarchy
4. **Creates and wires beans** via dependency injection
5. **Starts the embedded web server** (Tomcat on port 8083)
6. **Initializes data** (runs schema.sql and data.sql)
7. **Logs startup information**
8. **Waits for requests**

## Application Startup Sequence

When `SpringApplication.run()` executes, here's what happens:

### Phase 1: Environment Preparation
```
[main] INFO  o.s.b.SpringApplication - Starting Module03Application
[main] INFO  o.s.b.SpringApplication - No active profile set, falling back to default profiles: default
```

Spring loads properties from:
1. `application.properties` (built into JAR)
2. `application-{profile}.properties` (if profiles are active)
3. Environment variables
4. Command-line arguments

### Phase 2: Component Scanning
```
[main] INFO  o.s.c.a.ClassPathBeanDefinitionScanner - Identified candidate component class:
  com.techcorp.assistant.module03.config.MCPServerConfig
  com.techcorp.assistant.module03.controller.AssistantController
  com.techcorp.assistant.module03.service.ToolOrchestrator
  com.techcorp.assistant.module03.tool.CustomerDataTool
  com.techcorp.assistant.module03.tool.WeatherTool
```

### Phase 3: Bean Creation & Dependency Injection
```
[main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat initialized with port(s): 8083 (http)
[main] INFO  c.t.a.m.config.MCPServerConfig - Creating ChatModel bean
[main] INFO  c.t.a.m.service.ToolOrchestrator - ToolOrchestrator initialized with CustomerDataTool and WeatherTool
```

Spring creates beans in dependency order:
1. ChatModel (no dependencies)
2. CustomerDataTool (depends on JdbcTemplate)
3. WeatherTool (no dependencies)
4. ToolOrchestrator (depends on ChatModel, CustomerDataTool, WeatherTool)
5. AssistantController (depends on ToolOrchestrator)

### Phase 4: Database Initialization
```
[main] INFO  o.s.jdbc.datasource.init.ScriptUtils - Executing SQL script from file [schema.sql]
[main] INFO  o.s.jdbc.datasource.init.ScriptUtils - Executed SQL script from file [schema.sql] in 127 ms
[main] INFO  o.s.jdbc.datasource.init.ScriptUtils - Executing SQL script from file [data.sql]
[main] INFO  o.s.jdbc.datasource.init.ScriptUtils - Executed SQL script from file [data.sql] in 45 ms
```

Spring Boot automatically runs:
1. `src/main/resources/db/schema.sql` - Creates tables
2. `src/main/resources/db/data.sql` - Inserts sample data

### Phase 5: Web Server Start
```
[main] INFO  o.s.b.w.e.tomcat.TomcatWebServer - Tomcat started on port(s): 8083 (http)
[main] INFO  o.s.b.SpringApplication - Started Module03Application in 3.847 seconds
```

Your application is now running and ready to accept HTTP requests!

## Running the Application

### Method 1: Maven
```bash
cd src/module-03-tools-mcp
mvn spring-boot:run
```

**What this does:**
- Compiles Java code
- Downloads dependencies (first run only)
- Runs the main class
- Keeps the process running (Ctrl+C to stop)

### Method 2: IDE (IntelliJ IDEA)
1. Open the project in IntelliJ
2. Right-click `Module03Application.java`
3. Select "Run 'Module03Application'"

### Method 3: JAR File
```bash
mvn clean package
java -jar target/module-03-tools-mcp-1.0.0.jar
```

**Production deployment:**
```bash
# With environment variables
export OPENAI_API_KEY=sk-your-actual-key
export SPRING_PROFILES_ACTIVE=production
java -jar module-03-tools-mcp.jar
```

### Method 4: Docker
```dockerfile
FROM eclipse-temurin:17-jre
COPY target/module-03-tools-mcp-1.0.0.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```bash
docker build -t module03-assistant .
docker run -p 8083:8083 \
  -e OPENAI_API_KEY=sk-your-key \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/workshop_db \
  module03-assistant
```

## Configuration Files

### application.properties
```properties
# Module 03: Tools and MCP Configuration

# Application
spring.application.name=module-03-tools-mcp
server.port=8083

# PostgreSQL Database
spring.datasource.url=jdbc:postgresql://localhost:5432/workshop_db
spring.datasource.username=workshop
spring.datasource.password=workshop123
spring.datasource.driver-class-name=org.postgresql.Driver

# JdbcTemplate configuration
spring.jdbc.template.query-timeout=30

# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY:your-api-key-here}
openai.model.name=gpt-4o-mini

# Logging
logging.level.com.techcorp.assistant=DEBUG
logging.level.dev.langchain4j=DEBUG
```

**Key properties:**
- **spring.application.name**: Identifies your app in logs and monitoring
- **server.port**: Which port to run on (8083 to avoid conflicts)
- **spring.datasource.\***: Database connection settings
- **openai.api.key**: Your OpenAI API key (use environment variable!)
- **logging.level.\***: Controls log verbosity

### Environment-Specific Properties

**application-dev.properties** (development):
```properties
# Dev environment overrides
openai.api.key=sk-test-key
openai.model.name=gpt-3.5-turbo  # Cheaper for testing
logging.level.com.techcorp.assistant=DEBUG

# Use H2 in-memory database for dev
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
```

**application-prod.properties** (production):
```properties
# Production overrides
openai.api.key=${OPENAI_API_KEY}  # MUST come from environment
openai.model.name=gpt-4o-mini
logging.level.com.techcorp.assistant=INFO  # Less verbose

# Production database
spring.datasource.url=${DATABASE_URL}
```

**Activate a profile:**
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Or
export SPRING_PROFILES_ACTIVE=prod
java -jar app.jar
```

## Customizing Startup Behavior

### Custom Initialization Logic

Add initialization that runs after beans are created:

```java
@SpringBootApplication
public class Module03Application {

    public static void main(String[] args) {
        SpringApplication.run(Module03Application.class, args);
    }

    @Bean
    CommandLineRunner init(ToolOrchestrator orchestrator) {
        return args -> {
            System.out.println("Testing tool orchestrator...");
            String response = orchestrator.processRequest("Hello!");
            System.out.println("Response: " + response);
        };
    }
}
```

This runs once at startup and can:
- Verify database connectivity
- Test API keys
- Load initial data
- Run health checks

### Application Event Listeners

React to application lifecycle events:

```java
@SpringBootApplication
public class Module03Application {

    public static void main(String[] args) {
        SpringApplication.run(Module03Application.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println("AI Assistant is ready to serve requests!");
    }

    @EventListener(ContextClosedEvent.class)
    public void onShutdown() {
        System.out.println("AI Assistant shutting down...");
    }
}
```

## Troubleshooting Common Startup Issues

### Issue 1: Port Already in Use
```
Error: Web server failed to start. Port 8083 was already in use.
```

**Solution:**
- Change port in application.properties: `server.port=8084`
- Or kill the process using port 8083:
```bash
# macOS/Linux
lsof -ti:8083 | xargs kill -9

# Windows
netstat -ano | findstr :8083
taskkill /PID <PID> /F
```

### Issue 2: Database Connection Failed
```
Error: Failed to configure a DataSource: 'url' attribute is not specified
```

**Solution:**
- Ensure PostgreSQL is running
- Verify connection details in application.properties
- Check if database exists:
```bash
psql -h localhost -U workshop -d workshop_db
```

### Issue 3: OpenAI API Key Missing
```
Error: API key not provided
```

**Solution:**
Set environment variable:
```bash
export OPENAI_API_KEY=sk-your-actual-key
mvn spring-boot:run
```

### Issue 4: Bean Creation Failed
```
Error creating bean with name 'toolOrchestrator': Unsatisfied dependency
```

**Solution:**
- Check that all required dependencies are in pom.xml
- Verify component scanning is finding your classes
- Ensure classes have appropriate annotations (@Service, @Component, etc.)

### Issue 5: Schema/Data Scripts Failed
```
Error: SQL script execution failed
```

**Solution:**
- Check SQL syntax in schema.sql and data.sql
- Ensure scripts are in src/main/resources/db/
- Verify database permissions

## Monitoring and Health Checks

### Spring Boot Actuator

Add health endpoints:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

```properties
# Expose all actuator endpoints
management.endpoints.web.exposure.include=*
management.endpoint.health.show-details=always
```

Access health information:
```bash
# Basic health
curl http://localhost:8083/actuator/health

# Detailed health (shows database, disk, etc.)
curl http://localhost:8083/actuator/health | jq

# Application info
curl http://localhost:8083/actuator/info

# All beans
curl http://localhost:8083/actuator/beans

# Environment properties
curl http://localhost:8083/actuator/env
```

### Application Metrics

Track request metrics:
```bash
curl http://localhost:8083/actuator/metrics
curl http://localhost:8083/actuator/metrics/http.server.requests
```

## Production Deployment Checklist

Before deploying to production:

- [ ] Remove or externalize all hardcoded credentials
- [ ] Set `OPENAI_API_KEY` as environment variable
- [ ] Configure production database connection
- [ ] Set `logging.level` to INFO (not DEBUG)
- [ ] Enable HTTPS (configure SSL certificates)
- [ ] Set up monitoring and alerting
- [ ] Configure rate limiting
- [ ] Review and restrict CORS origins
- [ ] Set appropriate timeouts
- [ ] Configure connection pooling
- [ ] Test with production-like load
- [ ] Document API endpoints
- [ ] Set up automated backups
- [ ] Configure log aggregation (Splunk, ELK, etc.)
- [ ] Review security headers

## Key Takeaways

- **Module03Application is the entry point** that bootstraps the entire system
- **@SpringBootApplication enables auto-configuration** and component scanning
- **SpringApplication.run() initializes and starts** the application
- **Spring Boot auto-configures** web server, database, JSON serialization, and more
- **Component scanning finds and creates beans** automatically
- **application.properties configures** all aspects of the application
- **Multiple profiles enable environment-specific** configuration
- **Health checks and actuators** provide monitoring capabilities
- **Proper configuration is critical** for production deployment

## Conclusion: Your AI Assistant is Complete!

Congratulations! You've built a complete, production-ready AI assistant with tool integration. Your system can:

- Accept natural language queries via REST API
- Autonomously decide which tools to use
- Query PostgreSQL databases for customer information
- Call external APIs for weather data
- Orchestrate multi-tool workflows
- Return natural language responses

**What you've learned:**
- Function calling and the Model Context Protocol (MCP)
- Tool definition with @Tool annotations
- Database integration with Spring JdbcTemplate
- External API integration with RestTemplate
- AI orchestration with LangChain4j AiServices
- REST API design with Spring Boot
- Production-ready configuration and error handling

**Next steps:**
- Add more tools (email sender, calendar integration, etc.)
- Implement conversation memory for multi-turn dialogs
- Add authentication and authorization
- Implement rate limiting and caching
- Deploy to cloud platforms (AWS, Azure, GCP)
- Monitor and optimize performance
- Build a frontend (React, Vue, or Angular)

Your AI assistant is ready to serve users and can be extended with new capabilities as your needs grow!

---

**You've completed Module 03: Tools & Model Context Protocol Integration!**
