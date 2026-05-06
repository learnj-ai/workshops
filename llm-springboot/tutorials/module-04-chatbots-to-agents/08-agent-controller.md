# Agent Controller

## Introduction

The `AgentController` is the entry point to our agent system—it exposes a REST API that allows clients to interact with agents via HTTP. In this chapter, you'll learn how to design a flexible API that supports multiple agent modes, session management, and error handling.

## API Design

Our controller supports four agent modes through a single endpoint:

```
POST /api/v1/agent/execute
```

**Request**:
```json
{
  "message": "What's the weather in customer 12345's city?",
  "mode": "react",
  "sessionId": "abc-123"
}
```

**Response**:
```json
{
  "response": "The weather in Seattle is sunny and 72°F",
  "sessionId": "abc-123",
  "mode": "react"
}
```

### Supported Modes

1. **react**: ReAct agent with iterative reasoning and tool use
2. **multiagent**: Routes to the best specialized agent
3. **collaborative**: Gathers perspectives from all agents
4. **decompose**: Breaks down complex tasks into subtasks

## The AgentController Implementation

```java
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final ReActAgent reActAgent;
    private final MultiAgentOrchestrator multiAgentOrchestrator;
    private final TaskDecomposer taskDecomposer;
    private final ConversationMemoryService memoryService;

    public AgentController(
            ReActAgent reActAgent,
            MultiAgentOrchestrator multiAgentOrchestrator,
            TaskDecomposer taskDecomposer,
            ConversationMemoryService memoryService) {
        this.reActAgent = reActAgent;
        this.multiAgentOrchestrator = multiAgentOrchestrator;
        this.taskDecomposer = taskDecomposer;
        this.memoryService = memoryService;
    }

    @PostMapping("/execute")
    public ResponseEntity<AgentResponse> execute(@RequestBody AgentRequest request) {
        log.info("Agent execute request - mode: {}, session: {}", request.mode(), request.sessionId());

        try {
            // Get or create session
            String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                    ? request.sessionId()
                    : UUID.randomUUID().toString();

            // Add user message to memory
            memoryService.addMessage(sessionId, request.message());

            // Execute based on mode
            String response = switch (request.mode().toLowerCase()) {
                case "react" -> reActAgent.solve(request.message());
                case "multiagent" -> multiAgentOrchestrator.routeRequest(request.message());
                case "collaborative" -> multiAgentOrchestrator.collaborativeRequest(request.message());
                case "decompose" -> {
                    TaskDecomposer.TaskExecutionResult result =
                            taskDecomposer.executeComplexTask(request.message());
                    yield result.summary();
                }
                default -> "Unknown mode: " + request.mode();
            };

            // Add AI response to memory
            memoryService.addAiMessage(sessionId, response);

            return ResponseEntity.ok(new AgentResponse(response, sessionId, request.mode()));

        } catch (Exception e) {
            log.error("Error executing agent request", e);
            return ResponseEntity.internalServerError()
                    .body(new AgentResponse(
                            "An error occurred processing your request",
                            request.sessionId(),
                            request.mode()
                    ));
        }
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<String> clearSession(@PathVariable String sessionId) {
        log.info("Clearing session: {}", sessionId);
        memoryService.clearMemory(sessionId);
        return ResponseEntity.ok("Session cleared");
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Module 04: Agents - OK");
    }
}
```

### Key Features

**Mode-Based Routing**: A single endpoint supports all agent modes via switch expression.

**Session Management**: Auto-generates session IDs if not provided, ensuring conversation continuity.

**Memory Integration**: Saves user messages and AI responses to session memory automatically.

**Error Handling**: Catches exceptions and returns user-friendly error messages.

**Logging**: Logs all requests with mode and session for debugging.

## Request and Response DTOs

We use Java records for type-safe request/response handling:

```java
public record AgentRequest(
        String message,
        String mode,
        String sessionId
) {}

public record AgentResponse(
        String response,
        String sessionId,
        String mode
) {}
```

### Benefits of Records

**Immutability**: Records are immutable by default (no setters).

**Conciseness**: No boilerplate—compiler generates constructor, getters, equals(), hashCode(), toString().

**Type Safety**: Strong typing prevents mistakes.

**Pattern Matching**: Can be used in pattern matching (future Java versions).

## Session Management Flow

### First Request (No Session ID)

**Client Request**:
```json
{
  "message": "What's the weather in Seattle?",
  "mode": "react"
}
```

**Server Processing**:
1. `sessionId` is null
2. Generate new UUID: `"abc-123-def-456"`
3. Save user message to session `abc-123-def-456`
4. Execute agent
5. Save AI response to session `abc-123-def-456`

**Server Response**:
```json
{
  "response": "The weather in Seattle is sunny and 72°F",
  "sessionId": "abc-123-def-456",
  "mode": "react"
}
```

### Follow-Up Request (With Session ID)

**Client Request**:
```json
{
  "message": "And what about tomorrow?",
  "mode": "react",
  "sessionId": "abc-123-def-456"
}
```

**Server Processing**:
1. Use existing session `abc-123-def-456`
2. Retrieve conversation history (previous: "What's the weather in Seattle?" / "The weather is...")
3. Save new user message
4. Execute agent with context
5. Save AI response

**Server Response**:
```json
{
  "response": "Tomorrow in Seattle will be partly cloudy with a high of 68°F",
  "sessionId": "abc-123-def-456",
  "mode": "react"
}
```

The agent knows "tomorrow" refers to Seattle because it has conversation history.

## Mode Selection Guide

### When to Use Each Mode

**ReAct Mode**:
- Single-domain questions requiring tool use
- Multi-step reasoning problems
- Questions like "What's the weather where customer X lives?"

**MultiAgent Mode**:
- Questions that fit a clear specialist (support, docs, product)
- Cost-sensitive applications (only one agent executes)
- Questions like "How do I configure OAuth?" (routes to TechnicalDocAgent)

**Collaborative Mode**:
- Complex questions spanning multiple domains
- High-value queries requiring comprehensive answers
- Questions like "What's in the Enterprise plan and how do I set it up?" (product + docs)

**Decompose Mode**:
- Complex tasks requiring multiple steps
- Research or analysis tasks
- Questions like "Research our top customers and summarize their issues"

## Error Handling

### Global Exception Handler

While the controller has try-catch, you can add a global exception handler:

```java
@RestControllerAdvice
public class AgentExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("Invalid request: " + ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        return ResponseEntity.internalServerError()
                .body(new ErrorResponse("An unexpected error occurred"));
    }

    record ErrorResponse(String message) {}
}
```

### Validation

Add validation to request DTOs:

```java
public record AgentRequest(
        @NotBlank(message = "Message is required")
        String message,

        @NotBlank(message = "Mode is required")
        @Pattern(regexp = "react|multiagent|collaborative|decompose",
                 message = "Mode must be one of: react, multiagent, collaborative, decompose")
        String mode,

        String sessionId
) {}
```

Then enable validation in the controller:

```java
@PostMapping("/execute")
public ResponseEntity<AgentResponse> execute(@Valid @RequestBody AgentRequest request) {
    // Validation happens automatically before method executes
}
```

## Additional Endpoints

### Get Session History

```java
@GetMapping("/session/{sessionId}/history")
public ResponseEntity<List<MessageDTO>> getHistory(@PathVariable String sessionId) {
    List<ChatMessage> messages = memoryService.getHistory(sessionId);

    List<MessageDTO> dtos = messages.stream()
            .map(msg -> {
                if (msg instanceof UserMessage) {
                    return new MessageDTO("user", ((UserMessage) msg).text());
                } else if (msg instanceof AiMessage) {
                    return new MessageDTO("assistant", ((AiMessage) msg).text());
                }
                return null;
            })
            .filter(Objects::nonNull)
            .toList();

    return ResponseEntity.ok(dtos);
}

record MessageDTO(String role, String content) {}
```

### Get Session Metadata

```java
@GetMapping("/session/{sessionId}/metadata")
public ResponseEntity<SessionMetadata> getMetadata(@PathVariable String sessionId) {
    int messageCount = memoryService.getMessageCount(sessionId);

    return ResponseEntity.ok(new SessionMetadata(
            sessionId,
            messageCount,
            messageCount > 0
    ));
}

record SessionMetadata(String sessionId, int messageCount, boolean active) {}
```

### List Active Sessions

```java
@GetMapping("/sessions")
public ResponseEntity<List<String>> listSessions() {
    // This would require tracking active sessions in ConversationMemoryService
    List<String> activeSessions = memoryService.getActiveSessions();
    return ResponseEntity.ok(activeSessions);
}
```

## Testing the Controller

### Unit Tests with MockMvc

```java
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReActAgent reActAgent;

    @MockBean
    private MultiAgentOrchestrator multiAgentOrchestrator;

    @MockBean
    private TaskDecomposer taskDecomposer;

    @MockBean
    private ConversationMemoryService memoryService;

    @Test
    void execute_ReactMode_ReturnsResponse() throws Exception {
        when(reActAgent.solve(anyString())).thenReturn("Test response");

        mockMvc.perform(post("/api/v1/agent/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Test question",
                      "mode": "react"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Test response"))
                .andExpect(jsonPath("$.sessionId").exists())
                .andExpect(jsonPath("$.mode").value("react"));
    }

    @Test
    void execute_WithSessionId_ReusesSameSession() throws Exception {
        when(reActAgent.solve(anyString())).thenReturn("Follow-up response");

        mockMvc.perform(post("/api/v1/agent/execute")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "Follow-up question",
                      "mode": "react",
                      "sessionId": "existing-session-123"
                    }
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("existing-session-123"));

        verify(memoryService).addMessage("existing-session-123", "Follow-up question");
    }

    @Test
    void clearSession_DeletesMemory() throws Exception {
        mockMvc.perform(delete("/api/v1/agent/session/session-123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Session cleared"));

        verify(memoryService).clearMemory("session-123");
    }
}
```

### Integration Tests

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void execute_EndToEnd() {
        AgentRequest request = new AgentRequest(
            "What's my account status?",
            "multiagent",
            null
        );

        ResponseEntity<AgentResponse> response = restTemplate.postForEntity(
            "/api/v1/agent/execute",
            request,
            AgentResponse.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().response()).isNotBlank();
        assertThat(response.getBody().sessionId()).isNotBlank();
    }
}
```

## Security Considerations

### Authentication

Add authentication to require valid tokens:

```java
@PostMapping("/execute")
@PreAuthorize("hasRole('USER')")
public ResponseEntity<AgentResponse> execute(
        @RequestBody AgentRequest request,
        Authentication authentication) {

    String userId = authentication.getName();
    log.info("User {} executing agent in mode {}", userId, request.mode());

    // Proceed with execution
}
```

### Rate Limiting

Prevent abuse with rate limiting:

```java
@RateLimiter(name = "agent-api", fallbackMethod = "rateLimitFallback")
@PostMapping("/execute")
public ResponseEntity<AgentResponse> execute(@RequestBody AgentRequest request) {
    // Execution logic
}

public ResponseEntity<AgentResponse> rateLimitFallback(AgentRequest request, Exception ex) {
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new AgentResponse(
                    "Rate limit exceeded. Please try again later.",
                    request.sessionId(),
                    request.mode()
            ));
}
```

### Input Sanitization

Sanitize user inputs to prevent injection attacks:

```java
private String sanitizeInput(String input) {
    // Remove potentially dangerous characters
    return input.replaceAll("[<>]", "");
}
```

## CORS Configuration

If your frontend is on a different domain:

```java
@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/v1/agent/**")
                        .allowedOrigins("http://localhost:3000")
                        .allowedMethods("GET", "POST", "DELETE")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
```

## Monitoring and Metrics

Track agent usage:

```java
@PostMapping("/execute")
public ResponseEntity<AgentResponse> execute(@RequestBody AgentRequest request) {
    // Increment metric
    meterRegistry.counter("agent.requests",
            "mode", request.mode()).increment();

    Timer.Sample sample = Timer.start(meterRegistry);

    try {
        // Execute agent
        String response = ...;

        sample.stop(Timer.builder("agent.execution.time")
                .tag("mode", request.mode())
                .register(meterRegistry));

        return ResponseEntity.ok(new AgentResponse(response, sessionId, request.mode()));

    } catch (Exception e) {
        meterRegistry.counter("agent.errors",
                "mode", request.mode()).increment();
        throw e;
    }
}
```

## Summary

The `AgentController` provides a flexible REST API for agent interactions:

Key features:
- Single endpoint supporting multiple agent modes
- Automatic session management with UUID generation
- Integration with conversation memory
- Error handling and logging
- Health check endpoint

API design principles:
- Use records for type-safe DTOs
- Switch expressions for mode routing
- Automatic memory persistence
- Return session IDs for conversation continuity

Production considerations:
- Add authentication and authorization
- Implement rate limiting
- Sanitize user inputs
- Configure CORS for cross-origin requests
- Track metrics for monitoring

In the next chapter, we'll explore **configuration and deployment** to get your agent system running in production.

---

**Next Chapter**: [09 - Configuration and Deployment](./09-configuration.md)
