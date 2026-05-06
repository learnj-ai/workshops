# Chapter: AssistantController - REST API Layer

## Introduction: Exposing Your AI to the World

You've built tools, configured the AI model, created an orchestrator, and designed DTOs. Now it's time to expose all this functionality through a REST API so that web applications, mobile apps, or other services can interact with your AI assistant.

**AssistantController** is the HTTP endpoint layer that makes your AI accessible over the network. It's the front door to your system—handling incoming requests, delegating to the orchestrator, and returning formatted responses.

This is where HTTP meets AI, turning natural language queries into JSON responses.

## Code Deep Dive

Let's examine the AssistantController implementation:

```java
package com.techcorp.assistant.module03.controller;

import com.techcorp.assistant.module03.dto.ChatRequest;
import com.techcorp.assistant.module03.dto.ChatResponse;
import com.techcorp.assistant.module03.service.ToolOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for AI assistant with tool support.
 *
 * Provides endpoints for chat interactions where the LLM can
 * autonomously use tools to access databases and external APIs.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
    private static final Logger log = LoggerFactory.getLogger(AssistantController.class);

    private final ToolOrchestrator toolOrchestrator;

    public AssistantController(ToolOrchestrator toolOrchestrator) {
        this.toolOrchestrator = toolOrchestrator;
    }

    /**
     * Chat endpoint with automatic tool execution.
     *
     * The LLM will automatically invoke tools (CustomerDataTool, WeatherTool)
     * when needed to answer the user's question.
     *
     * Example queries:
     * - "What is customer 12345's email?"
     * - "Show me all open support tickets"
     * - "What's the weather in Boston?"
     * - "What open tickets does customer 12345 have?"
     *
     * @param request The user's chat message
     * @return AI-generated response, potentially augmented with tool data
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        log.info("Received chat request: {}", request.message());

        try {
            String response = toolOrchestrator.processRequest(request.message());
            return ResponseEntity.ok(new ChatResponse(response));

        } catch (Exception e) {
            log.error("Error processing chat request", e);
            return ResponseEntity.internalServerError()
                    .body(new ChatResponse("An error occurred processing your request"));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Module 03: Tools & MCP - OK");
    }
}
```

## Spring Web Annotations Explained

### @RestController
```java
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {
```

**@RestController** is a convenience annotation that combines:
- `@Controller`: Marks this as a Spring MVC controller
- `@ResponseBody`: Automatically serializes return values to JSON

**@RequestMapping("/api/v1/assistant")** sets the base path for all endpoints in this controller.

So a method with `@PostMapping("/chat")` becomes:
```
POST http://localhost:8083/api/v1/assistant/chat
```

**Why version your API (`/v1/`)?**
- Allows breaking changes in `/v2/` while supporting `/v1/`
- Clients can migrate at their own pace
- Standard practice in production APIs

### Constructor Injection
```java
private final ToolOrchestrator toolOrchestrator;

public AssistantController(ToolOrchestrator toolOrchestrator) {
    this.toolOrchestrator = toolOrchestrator;
}
```

Spring automatically injects the ToolOrchestrator bean.

**Benefits:**
- Explicit dependency declaration
- Enables immutability (final field)
- Easy to test (pass mock orchestrator)
- No `@Autowired` annotation needed (modern Spring style)

## The Chat Endpoint

### Method Signature
```java
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
```

**@PostMapping("/chat")**: Handles POST requests to `/api/v1/assistant/chat`

**@RequestBody ChatRequest request**:
- Spring reads the HTTP request body
- Deserializes JSON to ChatRequest object
- Validates (compact constructor runs)
- Passes to this method

**ResponseEntity<ChatResponse>**:
- Allows control over HTTP status codes
- Can set headers if needed
- Type-safe response body

### Request Logging
```java
log.info("Received chat request: {}", request.message());
```

**Why log at INFO level?**
- Every user interaction is significant
- Helps diagnose user issues
- Tracks API usage patterns
- Can be filtered in production if too verbose

**What to log:**
- User queries (be mindful of PII)
- Request IDs for tracing
- Processing times
- Error conditions

**What NOT to log:**
- Passwords, API keys, secrets
- Full customer records (may contain sensitive data)
- Excessive details in production (use DEBUG for that)

### Delegation to Service Layer
```java
String response = toolOrchestrator.processRequest(request.message());
return ResponseEntity.ok(new ChatResponse(response));
```

**Controller responsibilities:**
- Receive HTTP requests
- Validate input (handled by ChatRequest)
- Delegate to service layer
- Format HTTP responses
- Handle HTTP-specific concerns (status codes, headers)

**Controller does NOT:**
- Contain business logic (that's in ToolOrchestrator)
- Talk to databases directly (that's in tools)
- Call external APIs directly (that's in tools)

This separation keeps the architecture clean and testable.

### Success Response
```java
return ResponseEntity.ok(new ChatResponse(response));
```

**ResponseEntity.ok()** creates a response with:
- HTTP status: 200 OK
- Body: ChatResponse serialized to JSON
- Content-Type: application/json (automatic)

**Example:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "response": "Customer 12345's email is alice@example.com",
  "toolsUsed": ["getCustomerInfo"]
}
```

### Error Handling
```java
} catch (Exception e) {
    log.error("Error processing chat request", e);
    return ResponseEntity.internalServerError()
            .body(new ChatResponse("An error occurred processing your request"));
}
```

**ResponseEntity.internalServerError()** creates:
- HTTP status: 500 Internal Server Error
- Body: ChatResponse with error message
- Logged exception with stack trace

**Example:**
```http
HTTP/1.1 500 Internal Server Error
Content-Type: application/json

{
  "response": "An error occurred processing your request",
  "toolsUsed": []
}
```

**Why return a ChatResponse for errors?**
- Consistent response structure (always ChatResponse)
- Clients don't need special error parsing
- User-friendly error message
- Technical details are logged, not exposed

## The Health Check Endpoint

```java
@GetMapping("/health")
public ResponseEntity<String> health() {
    return ResponseEntity.ok("Module 03: Tools & MCP - OK");
}
```

**Purpose:**
- Monitoring: Automated systems can check if the service is up
- Load balancers: Use this to detect failed instances
- Deployment verification: Confirm the service started successfully
- Simple smoke test: Quick way to verify basic functionality

**Request:**
```http
GET http://localhost:8083/api/v1/assistant/health
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: text/plain

Module 03: Tools & MCP - OK
```

**Enhanced health check:**
```java
@GetMapping("/health")
public ResponseEntity<Map<String, Object>> health() {
    Map<String, Object> health = new HashMap<>();
    health.put("status", "UP");
    health.put("module", "Module 03: Tools & MCP");
    health.put("timestamp", Instant.now());
    health.put("database", checkDatabase() ? "UP" : "DOWN");
    health.put("openai", checkOpenAI() ? "UP" : "DOWN");
    return ResponseEntity.ok(health);
}
```

Response:
```json
{
  "status": "UP",
  "module": "Module 03: Tools & MCP",
  "timestamp": "2024-05-06T14:30:00Z",
  "database": "UP",
  "openai": "UP"
}
```

## Testing the API

### Using curl

**Basic chat request:**
```bash
curl -X POST http://localhost:8083/api/v1/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What is customer 12345'\''s email?"}'
```

**Response:**
```json
{
  "response": "Customer 12345's email address is alice.johnson@example.com. The customer's name is Alice Johnson and they have a premium subscription plan.",
  "toolsUsed": ["getCustomerInfo"]
}
```

**Weather query:**
```bash
curl -X POST http://localhost:8083/api/v1/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "What'\''s the weather in Boston?"}'
```

**Support tickets:**
```bash
curl -X POST http://localhost:8083/api/v1/assistant/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "Show me all open support tickets"}'
```

**Health check:**
```bash
curl http://localhost:8083/api/v1/assistant/health
```

### Using Postman or Insomnia

**Create a new request:**
- Method: POST
- URL: `http://localhost:8083/api/v1/assistant/chat`
- Headers: `Content-Type: application/json`
- Body (JSON):
```json
{
  "message": "What's customer 12345's email?"
}
```

### Using HTTPie (Cleaner Syntax)
```bash
# Install: brew install httpie (macOS) or apt install httpie (Linux)

# Chat request
http POST localhost:8083/api/v1/assistant/chat message="What's customer 12345's email?"

# Health check
http GET localhost:8083/api/v1/assistant/health
```

## Advanced Error Handling

### Custom Exception Handler

For better error responses, add a global exception handler:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("Invalid request: {}", e.getMessage());
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid request: " + e.getMessage()));
    }

    @ExceptionHandler(HttpClientErrorException.TooManyRequests.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(HttpClientErrorException.TooManyRequests e) {
        log.warn("OpenAI rate limit exceeded");
        return ResponseEntity.status(429)
                .body(new ErrorResponse("Rate limit exceeded. Please try again later."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("An unexpected error occurred"));
    }
}

record ErrorResponse(String error, long timestamp) {
    public ErrorResponse(String error) {
        this(error, System.currentTimeMillis());
    }
}
```

Now different errors return appropriate status codes:
- 400 Bad Request: Invalid input
- 429 Too Many Requests: Rate limiting
- 500 Internal Server Error: Unexpected failures

### Validation Errors

Enable Bean Validation:
```java
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
    // @Valid triggers validation
}
```

With validation annotations:
```java
public record ChatRequest(
    @NotBlank(message = "Message is required")
    @Size(max = 5000, message = "Message too long")
    String message
) {}
```

Invalid request returns:
```http
HTTP/1.1 400 Bad Request

{
  "error": "Message is required",
  "timestamp": 1715008200000
}
```

## CORS Configuration

If your API is called from web browsers, enable CORS:

```java
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:3000")  // Your frontend
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
```

Or per-controller:
```java
@RestController
@RequestMapping("/api/v1/assistant")
@CrossOrigin(origins = "http://localhost:3000")
public class AssistantController {
    // ...
}
```

## API Documentation

### OpenAPI/Swagger

Add Springdoc OpenAPI:
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

Annotate for better docs:
```java
@RestController
@RequestMapping("/api/v1/assistant")
@Tag(name = "AI Assistant", description = "Chat endpoints with tool support")
public class AssistantController {

    @PostMapping("/chat")
    @Operation(
        summary = "Chat with AI assistant",
        description = "Send a message and receive an AI-generated response. The assistant can use tools to access customer data and weather information."
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Successful response"),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "500", description = "Server error")
    })
    public ResponseEntity<ChatResponse> chat(
            @RequestBody
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Chat message",
                required = true,
                content = @Content(
                    schema = @Schema(implementation = ChatRequest.class)
                )
            )
            ChatRequest request) {
        // ...
    }
}
```

Access interactive docs at:
```
http://localhost:8083/swagger-ui.html
```

## Request/Response Examples

### Example 1: Simple Customer Query

**Request:**
```http
POST /api/v1/assistant/chat
Content-Type: application/json

{
  "message": "What is customer 12345's subscription plan?"
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "response": "Customer 12345 (Alice Johnson) is on the premium subscription plan.",
  "toolsUsed": ["getCustomerInfo"]
}
```

### Example 2: Weather Query

**Request:**
```http
POST /api/v1/assistant/chat
Content-Type: application/json

{
  "message": "What's the weather like in Seattle?"
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "response": "The current weather in Seattle is light rain with a temperature of 14°C (57°F). The humidity is 85% and there's a wind speed of 10 km/h from the south.",
  "toolsUsed": ["getCurrentWeather"]
}
```

### Example 3: Ticket Search

**Request:**
```http
POST /api/v1/assistant/chat
Content-Type: application/json

{
  "message": "Show me all pending support tickets"
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "response": "I found 3 pending support tickets:\n\n1. Ticket #2 - Customer: Alice Johnson\n   Subject: Billing discrepancy for March invoice\n\n2. Ticket #6 - Customer: Carol Martinez\n   Subject: Export functionality broken\n\n3. Ticket #8 - Customer: Emma Wilson\n   Subject: Integration with third-party service",
  "toolsUsed": ["searchTickets"]
}
```

### Example 4: Invalid Request

**Request:**
```http
POST /api/v1/assistant/chat
Content-Type: application/json

{
  "message": ""
}
```

**Response:**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "error": "Message cannot be null or empty",
  "timestamp": 1715008200000
}
```

## Performance Considerations

### Response Time Breakdown

Typical request:
- Network (client → server): 10-50ms
- Controller overhead: <5ms
- Orchestrator + LLM + tools: 1000-3000ms
- JSON serialization: <5ms
- Network (server → client): 10-50ms
- **Total: 1-3 seconds**

### Optimization Strategies

1. **Async Processing** (for long requests):
```java
@PostMapping("/chat")
public CompletableFuture<ResponseEntity<ChatResponse>> chat(@RequestBody ChatRequest request) {
    return CompletableFuture.supplyAsync(() -> {
        String response = toolOrchestrator.processRequest(request.message());
        return ResponseEntity.ok(new ChatResponse(response));
    });
}
```

2. **Caching** (for repeated queries):
```java
@Cacheable(value = "chat-responses", key = "#request.message")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    // ...
}
```

3. **Rate Limiting** (protect against abuse):
```java
@PostMapping("/chat")
@RateLimiter(name = "chat", fallbackMethod = "chatFallback")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    // ...
}
```

## Key Takeaways

- **AssistantController is the HTTP entry point** for your AI system
- **@RestController and @RequestMapping** define REST endpoints
- **@RequestBody deserializes JSON** to DTOs automatically
- **ResponseEntity provides control** over HTTP status codes and headers
- **Controllers delegate to services** - no business logic in controllers
- **Error handling returns user-friendly messages** while logging technical details
- **Health checks enable monitoring** and deployment verification
- **CORS configuration is needed** for browser-based clients
- **API documentation improves developer experience**

## Next Steps: Running the Complete Application

Now that you've built all the components, it's time to see the complete system in action.

In the next chapter, **Module03Application**, you'll learn how to:
- Understand the Spring Boot application class
- Run the application locally
- Test the complete system end-to-end
- Troubleshoot common issues

---

**Continue to the next chapter to launch your AI assistant!**
