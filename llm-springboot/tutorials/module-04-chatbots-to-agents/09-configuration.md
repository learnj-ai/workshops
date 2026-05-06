# Configuration and Deployment

## Introduction

In this chapter, we'll explore how to configure the agent system for different environments and deploy it to production. You'll learn about configuration management, environment-specific settings, and deployment best practices.

## AgentConfig Class

The central configuration class sets up all agent dependencies:

```java
@Configuration
public class AgentConfig {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Value("${openai.model.name:gpt-4o-mini}")
    private String modelName;

    @Value("${openai.temperature:0.7}")
    private double temperature;

    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        return template;
    }
}
```

### Configuration Patterns

**@Value Injection**: Injects properties from `application.properties` or environment variables.

**Default Values**: `${property:default}` syntax provides fallbacks (e.g., `gpt-4o-mini` if model name not specified).

**Bean Definition**: `@Bean` methods create Spring-managed beans that can be injected elsewhere.

**Builder Pattern**: LangChain4j models use builders for clean configuration.

## Application Properties

### application.properties

```properties
# Application
spring.application.name=module-04-chatbots-to-agents
server.port=8084

# OpenAI Configuration
openai.api.key=${OPENAI_API_KEY}
openai.model.name=gpt-4o-mini
openai.temperature=0.7

# Redis Configuration
spring.redis.host=localhost
spring.redis.port=6379
spring.redis.password=${REDIS_PASSWORD:}
spring.redis.timeout=2000ms

# Database Configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/techcorp
spring.datasource.username=${DB_USERNAME:postgres}
spring.datasource.password=${DB_PASSWORD:postgres}
spring.datasource.driver-class-name=org.postgresql.Driver

# Agent Configuration
agent.react.max-iterations=5
agent.memory.max-messages=20

# Logging
logging.level.com.techcorp.assistant.module04=DEBUG
logging.level.dev.langchain4j=INFO
```

### Environment-Specific Profiles

**application-dev.properties** (Development):
```properties
# Development settings
openai.temperature=0.9
agent.react.max-iterations=10
logging.level.com.techcorp.assistant.module04=DEBUG

# Use H2 in-memory database for dev
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
```

**application-prod.properties** (Production):
```properties
# Production settings
openai.temperature=0.7
agent.react.max-iterations=5
logging.level.com.techcorp.assistant.module04=INFO

# Production database
spring.datasource.url=jdbc:postgresql://prod-db.example.com:5432/techcorp
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
```

**Activate Profile**:
```bash
# Development
java -jar agent-app.jar --spring.profiles.active=dev

# Production
java -jar agent-app.jar --spring.profiles.active=prod
```

## Environment Variables

For sensitive configuration, use environment variables:

### Local Development

Create `.env` file:
```bash
export OPENAI_API_KEY=sk-...
export REDIS_PASSWORD=your-redis-password
export DB_USERNAME=postgres
export DB_PASSWORD=your-db-password
```

Load before running:
```bash
source .env
mvn spring-boot:run
```

### Docker

Pass environment variables to Docker:
```bash
docker run -e OPENAI_API_KEY=sk-... \
           -e REDIS_PASSWORD=... \
           -e DB_USERNAME=... \
           -e DB_PASSWORD=... \
           techcorp/agent-app:latest
```

### Kubernetes

Use ConfigMaps and Secrets:

**ConfigMap** (non-sensitive config):
```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: agent-config
data:
  openai.model.name: "gpt-4o-mini"
  agent.react.max-iterations: "5"
  spring.redis.host: "redis-service"
```

**Secret** (sensitive data):
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: agent-secrets
type: Opaque
data:
  openai.api.key: c2stLi4uCg==  # base64 encoded
  redis.password: eW91ci1wYXNzd29yZAo=
```

**Deployment**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: agent-app
spec:
  template:
    spec:
      containers:
      - name: agent-app
        image: techcorp/agent-app:latest
        envFrom:
        - configMapRef:
            name: agent-config
        - secretRef:
            name: agent-secrets
```

## Docker Deployment

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

# Create app directory
WORKDIR /app

# Copy the JAR file
COPY target/module-04-chatbots-to-agents-*.jar app.jar

# Expose port
EXPOSE 8084

# Health check
HEALTHCHECK --interval=30s --timeout=3s \
  CMD wget -q -O /dev/null http://localhost:8084/api/v1/agent/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Build and Run

```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t techcorp/agent-app:latest .

# Run with Docker Compose
docker-compose up
```

### docker-compose.yml

```yaml
version: '3.8'

services:
  agent-app:
    image: techcorp/agent-app:latest
    ports:
      - "8084:8084"
    environment:
      OPENAI_API_KEY: ${OPENAI_API_KEY}
      SPRING_REDIS_HOST: redis
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/techcorp
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
    depends_on:
      - redis
      - postgres

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data

  postgres:
    image: postgres:15-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: techcorp
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres-data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql

volumes:
  redis-data:
  postgres-data:
```

## Database Initialization

### init.sql

```sql
-- Create customers table
CREATE TABLE IF NOT EXISTS customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    subscription_plan VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create support tickets table
CREATE TABLE IF NOT EXISTS support_tickets (
    ticket_id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) REFERENCES customers(customer_id),
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert sample data
INSERT INTO customers (customer_id, name, email, subscription_plan) VALUES
    ('12345', 'John Doe', 'john@example.com', 'Enterprise'),
    ('67890', 'Jane Smith', 'jane@example.com', 'Pro')
ON CONFLICT (customer_id) DO NOTHING;

INSERT INTO support_tickets (customer_id, subject, status) VALUES
    ('12345', 'Login issues', 'open'),
    ('12345', 'Feature request', 'pending'),
    ('67890', 'Billing question', 'open')
ON CONFLICT DO NOTHING;
```

## Production Considerations

### 1. Connection Pooling

Configure Hikari connection pool:

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

### 2. Redis Clustering

For high availability:

```properties
spring.redis.cluster.nodes=redis-1:6379,redis-2:6379,redis-3:6379
spring.redis.cluster.max-redirects=3
```

### 3. Timeouts

Set reasonable timeouts:

```properties
# OpenAI API timeout
openai.timeout=60s

# Redis timeout
spring.redis.timeout=2000ms

# Database query timeout
spring.datasource.hikari.connection-timeout=30000
```

### 4. Retry Logic

Add retry for transient failures:

```java
@Bean
public ChatModel chatModel() {
    return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(modelName)
            .maxRetries(3)
            .build();
}
```

### 5. Circuit Breaker

Prevent cascading failures:

```java
@CircuitBreaker(name = "openai", fallbackMethod = "fallbackResponse")
public String solve(String question) {
    return reActAgent.solve(question);
}

public String fallbackResponse(String question, Exception ex) {
    log.error("Circuit breaker activated for question: {}", question, ex);
    return "The AI service is temporarily unavailable. Please try again later.";
}
```

## Monitoring and Observability

### Actuator Endpoints

Add Spring Boot Actuator:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

Configure endpoints:

```properties
management.endpoints.web.exposure.include=health,info,metrics,prometheus
management.endpoint.health.show-details=always
management.metrics.export.prometheus.enabled=true
```

### Custom Health Indicators

```java
@Component
public class OpenAIHealthIndicator implements HealthIndicator {

    private final ChatModel chatModel;

    @Override
    public Health health() {
        try {
            chatModel.chat("ping");
            return Health.up().withDetail("openai", "reachable").build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("openai", "unreachable")
                    .withException(e)
                    .build();
        }
    }
}
```

### Prometheus Metrics

Track custom metrics:

```java
@Service
public class MetricsService {
    private final MeterRegistry meterRegistry;

    public void recordAgentExecution(String mode, long durationMs) {
        Timer.builder("agent.execution")
                .tag("mode", mode)
                .register(meterRegistry)
                .record(durationMs, TimeUnit.MILLISECONDS);
    }

    public void recordToolInvocation(String toolName) {
        meterRegistry.counter("agent.tool.invocations", "tool", toolName).increment();
    }
}
```

## Logging Configuration

### logback-spring.xml

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/agent-app.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/agent-app.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

    <logger name="com.techcorp.assistant.module04" level="DEBUG"/>
    <logger name="dev.langchain4j" level="INFO"/>
</configuration>
```

## Testing in Different Environments

### Local Testing

```bash
# Set environment
export SPRING_PROFILES_ACTIVE=dev
export OPENAI_API_KEY=sk-...

# Run application
mvn spring-boot:run

# Test endpoint
curl -X POST http://localhost:8084/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"message": "What is customer 12345?", "mode": "react"}'
```

### Docker Testing

```bash
# Build and run
docker-compose up --build

# Test
curl -X POST http://localhost:8084/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"message": "Test message", "mode": "react"}'

# View logs
docker-compose logs -f agent-app
```

### Kubernetes Testing

```bash
# Apply configs
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/deployment.yaml
kubectl apply -f k8s/service.yaml

# Check status
kubectl get pods
kubectl logs -f deployment/agent-app

# Port forward for testing
kubectl port-forward service/agent-app 8084:8084

# Test
curl -X POST http://localhost:8084/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"message": "Test message", "mode": "react"}'
```

## Summary

Configuration and deployment best practices:

**Configuration Management**:
- Use `application.properties` for base config
- Profile-specific properties for environment overrides
- Environment variables for sensitive data
- ConfigMaps and Secrets for Kubernetes

**Deployment Options**:
- Local: `mvn spring-boot:run`
- Docker: `docker-compose up`
- Kubernetes: Apply manifests and scale

**Production Readiness**:
- Connection pooling for databases
- Redis clustering for high availability
- Timeouts and retries for resilience
- Circuit breakers for fault tolerance

**Monitoring**:
- Spring Boot Actuator for health checks
- Prometheus metrics for monitoring
- Structured logging for debugging
- Custom health indicators for dependencies

In the final chapter, we'll put it all together with real-world examples and best practices.

---

**Next Chapter**: [10 - Real-World Examples and Best Practices](./10-examples-best-practices.md)
