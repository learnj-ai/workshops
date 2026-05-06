# Chapter: WeatherTool - External API Integration

## Introduction: Connecting AI to the World

While database tools let your AI access internal data, external API integration opens up the entire world of third-party services. Weather APIs, payment gateways, mapping services, news feeds—all become accessible to your AI assistant through tool integration.

**WeatherTool** demonstrates how to integrate external REST APIs with your AI system. It shows you how to call third-party services, handle API failures gracefully, and present external data in a format that language models can understand and use.

This tool is implemented with a mock response for workshop purposes, but we'll also show you how to integrate with a real weather API like OpenWeatherMap.

## Code Deep Dive

Let's examine the WeatherTool implementation:

```java
package com.techcorp.assistant.module03.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class WeatherTool {
    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private final RestTemplate restTemplate;

    @Value("${weather.api.key:demo}")
    private String apiKey;

    public WeatherTool() {
        this.restTemplate = new RestTemplate();
    }

    @Tool("Retrieves current weather information for a specified city including temperature and conditions")
    public String getCurrentWeather(@P("The city name to get weather for") String city) {
        log.debug("Tool invoked: getCurrentWeather({})", city);

        try {
            // For workshop purposes, using a simplified mock response
            // In production, replace with actual OpenWeatherMap API call:
            // String url = String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric", city, apiKey);
            // Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            // Mock weather data for common cities
            String weatherInfo = switch (city.toLowerCase().trim()) {
                case "boston" -> """
                    Current Weather in Boston:
                    - Temperature: 18°C (64°F)
                    - Conditions: Partly cloudy
                    - Humidity: 65%
                    - Wind: 12 km/h NE
                    """;
                case "new york", "nyc" -> """
                    Current Weather in New York:
                    - Temperature: 22°C (72°F)
                    - Conditions: Sunny
                    - Humidity: 55%
                    - Wind: 8 km/h SW
                    """;
                case "san francisco" -> """
                    Current Weather in San Francisco:
                    - Temperature: 16°C (61°F)
                    - Conditions: Foggy
                    - Humidity: 80%
                    - Wind: 15 km/h W
                    """;
                case "seattle" -> """
                    Current Weather in Seattle:
                    - Temperature: 14°C (57°F)
                    - Conditions: Light rain
                    - Humidity: 85%
                    - Wind: 10 km/h S
                    """;
                case "chicago" -> """
                    Current Weather in Chicago:
                    - Temperature: 20°C (68°F)
                    - Conditions: Clear
                    - Humidity: 60%
                    - Wind: 18 km/h NW
                    """;
                default -> String.format("""
                    Current Weather in %s:
                    - Temperature: 19°C (66°F)
                    - Conditions: Partly cloudy
                    - Humidity: 62%%
                    - Wind: 10 km/h W
                    (Note: Using sample data for workshop. In production, integrate with OpenWeatherMap API.)
                    """, city);
            };

            return weatherInfo;

        } catch (Exception e) {
            log.error("Error fetching weather for city: {}", city, e);
            return "Unable to retrieve weather information at this time. The weather service may be temporarily unavailable.";
        }
    }
}
```

## Architecture and Design Decisions

### Configuration Injection
```java
@Value("${weather.api.key:demo}")
private String apiKey;
```

**@Value annotation** injects configuration from application.properties:
- `weather.api.key` is the property name
- `:demo` is the default value if the property isn't set
- This keeps sensitive API keys out of your source code

In `application.properties`:
```properties
weather.api.key=${WEATHER_API_KEY:your-api-key-here}
```

This creates a two-level default:
1. Uses environment variable `WEATHER_API_KEY` if set
2. Falls back to `your-api-key-here` for local development
3. Falls back to `demo` if neither is set

**Why this matters:**
- Production: Set `WEATHER_API_KEY` environment variable
- Development: Use default or override in application-local.properties
- Security: Never commit API keys to version control

### RestTemplate for HTTP Calls
```java
private final RestTemplate restTemplate;

public WeatherTool() {
    this.restTemplate = new RestTemplate();
}
```

**RestTemplate** is Spring's classic HTTP client:
- Simple API for making REST calls
- Handles JSON serialization/deserialization automatically
- Supports HTTP error handling and retries
- Well-documented and battle-tested

**Alternative (Modern Spring):**
```java
// Spring WebFlux approach (reactive)
private final WebClient webClient = WebClient.create();

public String getCurrentWeather(String city) {
    return webClient.get()
        .uri("https://api.openweathermap.org/data/2.5/weather?q={city}&appid={key}", city, apiKey)
        .retrieve()
        .bodyToMono(String.class)
        .block();
}
```

For synchronous tools like this, RestTemplate is simpler and perfectly adequate.

## Mock Implementation for Development

The current implementation uses a mock response:

```java
String weatherInfo = switch (city.toLowerCase().trim()) {
    case "boston" -> """
        Current Weather in Boston:
        - Temperature: 18°C (64°F)
        - Conditions: Partly cloudy
        - Humidity: 65%
        - Wind: 12 km/h NE
        """;
    // ... more cities
    default -> String.format("""
        Current Weather in %s:
        - Temperature: 19°C (66°F)
        - Conditions: Partly cloudy
        - Humidity: 62%%
        - Wind: 10 km/h W
        (Note: Using sample data for workshop.)
        """, city);
};
```

**Why use a mock during development?**
1. **No API key required**: Students can run the workshop without signing up for services
2. **No rate limits**: Unlimited testing without worrying about API quotas
3. **Faster development**: No network latency, immediate responses
4. **Predictable testing**: Same results every time, easier to verify
5. **Works offline**: No internet connection required for development

**Pattern Matching Switch (Java 14+):**
- Modern, expressive syntax for conditional logic
- Pattern matching allows multiple cases: `case "new york", "nyc" ->`
- Arrow syntax (`->`) is cleaner than traditional case/break

## Production Implementation with Real API

Here's how to integrate with OpenWeatherMap API:

### Step 1: Sign Up and Get API Key
1. Go to https://openweathermap.org/api
2. Sign up for a free account
3. Get your API key from the dashboard
4. Add to environment variables or application.properties

### Step 2: Update the Tool Implementation
```java
@Tool("Retrieves current weather information for a specified city including temperature and conditions")
public String getCurrentWeather(@P("The city name to get weather for") String city) {
    log.debug("Tool invoked: getCurrentWeather({})", city);

    try {
        // Construct the API URL
        String url = String.format(
            "https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s&units=metric",
            city,
            apiKey
        );

        // Make the API call
        Map<String, Object> response = restTemplate.getForObject(url, Map.class);

        // Parse the response
        if (response == null) {
            return "Unable to retrieve weather information for " + city;
        }

        // Extract weather data
        Map<String, Object> main = (Map<String, Object>) response.get("main");
        List<Map<String, Object>> weatherList = (List<Map<String, Object>>) response.get("weather");
        Map<String, Object> wind = (Map<String, Object>) response.get("wind");

        double tempC = ((Number) main.get("temp")).doubleValue();
        double tempF = tempC * 9/5 + 32;
        String conditions = (String) weatherList.get(0).get("description");
        int humidity = ((Number) main.get("humidity")).intValue();
        double windSpeed = ((Number) wind.get("speed")).doubleValue();

        // Format the response
        return String.format("""
            Current Weather in %s:
            - Temperature: %.1f°C (%.1f°F)
            - Conditions: %s
            - Humidity: %d%%
            - Wind: %.1f m/s
            """,
            city,
            tempC,
            tempF,
            conditions,
            humidity,
            windSpeed
        );

    } catch (Exception e) {
        log.error("Error fetching weather for city: {}", city, e);
        return "Unable to retrieve weather information at this time. The weather service may be temporarily unavailable.";
    }
}
```

### Step 3: Handle API-Specific Errors

Real APIs can fail in various ways. Enhance error handling:

```java
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

try {
    // API call
} catch (HttpClientErrorException.NotFound e) {
    log.warn("City not found: {}", city);
    return "I couldn't find weather information for '" + city + "'. Please check the city name and try again.";
} catch (HttpClientErrorException.Unauthorized e) {
    log.error("Invalid weather API key");
    return "Weather service is misconfigured. Please contact support.";
} catch (HttpServerErrorException e) {
    log.error("Weather API server error", e);
    return "The weather service is experiencing issues. Please try again later.";
} catch (Exception e) {
    log.error("Unexpected error fetching weather", e);
    return "Unable to retrieve weather information at this time.";
}
```

## Response Format Design

The weather information is returned in a structured, readable format:

```
Current Weather in Boston:
- Temperature: 18°C (64°F)
- Conditions: Partly cloudy
- Humidity: 65%
- Wind: 12 km/h NE
```

**Why this format works well:**

1. **Clear header**: City name is prominently displayed
2. **Structured fields**: Each piece of information on its own line
3. **Both units**: Temperature in both Celsius and Fahrenheit
4. **Complete information**: All major weather factors included
5. **Human-readable**: The LLM can directly quote or paraphrase this

The LLM can then respond naturally:
> "The current weather in Boston is partly cloudy with a temperature of 18°C (64°F). The humidity is 65% and there's a light wind from the northeast at 12 km/h."

## Advanced Integration Patterns

### Pattern 1: Caching API Responses

Weather doesn't change every second. Cache responses to reduce API calls:

```java
import org.springframework.cache.annotation.Cacheable;

@Cacheable(value = "weather", key = "#city", unless = "#result == null")
@Tool("Retrieves current weather information for a specified city")
public String getCurrentWeather(@P("The city name to get weather for") String city) {
    // API call
}
```

With cache configuration:
```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        cacheManager.setCaches(Arrays.asList(
            new ConcurrentMapCache("weather")
        ));
        return cacheManager;
    }
}
```

Or with TTL:
```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

```properties
# application.properties
spring.cache.caffeine.spec=expireAfterWrite=5m
```

### Pattern 2: Rate Limiting

Protect against API quota exhaustion:

```java
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;

@Component
public class WeatherTool {
    private final Bucket bucket;

    public WeatherTool() {
        // Allow 10 calls per minute
        Bandwidth limit = Bandwidth.classic(10, Refill.intervally(10, Duration.ofMinutes(1)));
        this.bucket = Bucket.builder().addLimit(limit).build();
    }

    @Tool("Retrieves current weather information for a specified city")
    public String getCurrentWeather(String city) {
        if (!bucket.tryConsume(1)) {
            return "Weather service is temporarily rate-limited. Please try again in a moment.";
        }
        // API call
    }
}
```

### Pattern 3: Fallback Mechanisms

Provide degraded service when the API is down:

```java
@Tool("Retrieves current weather information for a specified city")
public String getCurrentWeather(String city) {
    try {
        // Try real API first
        return callWeatherAPI(city);
    } catch (Exception e) {
        log.warn("Primary weather API failed, using fallback", e);
        try {
            // Try alternative API
            return callBackupWeatherAPI(city);
        } catch (Exception e2) {
            log.error("All weather APIs failed", e2);
            return "Weather information is temporarily unavailable. Please try again later.";
        }
    }
}
```

## Configuration Best Practices

### Environment-Specific Configuration

**application.properties** (defaults):
```properties
weather.api.key=${WEATHER_API_KEY:demo}
weather.api.base-url=https://api.openweathermap.org/data/2.5
weather.api.timeout=5000
```

**application-dev.properties** (development):
```properties
weather.api.key=demo
# Use mock responses in dev
weather.api.mock=true
```

**application-prod.properties** (production):
```properties
# API key comes from environment variable
weather.api.mock=false
weather.api.timeout=3000
# Stricter timeout in production
```

### Secure API Key Management

**Never do this:**
```java
private String apiKey = "abc123secretkey";  // DON'T!
```

**Always do this:**
```java
@Value("${weather.api.key}")
private String apiKey;
```

**In production:**
1. Set environment variable: `export WEATHER_API_KEY=abc123secretkey`
2. Or use secret management: AWS Secrets Manager, HashiCorp Vault, etc.
3. Or use Spring Cloud Config for centralized configuration

## Testing Strategies

### Unit Test with Mock
```java
@Test
void testGetCurrentWeather() {
    WeatherTool weatherTool = new WeatherTool();
    String result = weatherTool.getCurrentWeather("Boston");

    assertThat(result).contains("Boston");
    assertThat(result).contains("Temperature");
    assertThat(result).contains("Conditions");
}
```

### Integration Test with WireMock
```java
@Test
void testGetCurrentWeather_WithMockAPI() {
    // Set up WireMock to simulate weather API
    stubFor(get(urlPathEqualTo("/data/2.5/weather"))
        .willReturn(aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody("""
                {
                  "main": {"temp": 18.0, "humidity": 65},
                  "weather": [{"description": "partly cloudy"}],
                  "wind": {"speed": 3.3}
                }
                """)));

    WeatherTool weatherTool = new WeatherTool();
    String result = weatherTool.getCurrentWeather("Boston");

    assertThat(result).contains("18.0°C");
    assertThat(result).contains("partly cloudy");
}
```

## Key Takeaways

- **WeatherTool demonstrates external API integration** using Spring's RestTemplate
- **Mock implementations enable development** without API keys or network dependencies
- **@Value injects configuration** from properties files or environment variables
- **Error handling is critical** - external APIs can fail in many ways
- **Caching reduces API calls** and improves response times
- **Rate limiting protects** against quota exhaustion
- **Structured responses** make it easy for LLMs to understand and use the data
- **Security first**: Never hardcode API keys, use environment variables or secret management

## Next Steps: Configuring the MCP Server

Now that you have both database and API tools, you need to connect them to a language model that can call them.

In the next chapter, **MCPServerConfig**, you'll learn how to:
- Configure the OpenAI ChatModel with API credentials
- Set up model parameters (temperature, timeout, logging)
- Understand the Model Context Protocol (MCP)
- Prepare the foundation for tool orchestration

---

**Continue to the next chapter to configure your AI model!**
