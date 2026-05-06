# Chapter: ChatRequest & ChatResponse - Data Transfer Objects

## Introduction: Designing Clean APIs

Every REST API needs a well-defined contract between client and server. What data should clients send? What format will responses take? How do we ensure type safety and validation?

**ChatRequest** and **ChatResponse** are the Data Transfer Objects (DTOs) that define this contract for your AI assistant API. They're simple Java records that represent the JSON data flowing in and out of your endpoints.

Good DTO design makes your API:
- **Predictable**: Clients know exactly what to send and expect
- **Type-safe**: Compile-time checking prevents many bugs
- **Validated**: Invalid requests are caught before processing
- **Documented**: Clear structure aids API documentation
- **Evolvable**: Easy to extend with new fields

## ChatRequest: The User's Query

### Code Implementation

```java
package com.techcorp.assistant.module03.dto;

/**
 * Request DTO for chat endpoint.
 *
 * @param message The user's message/query
 */
public record ChatRequest(String message) {
    public ChatRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
    }
}
```

### Why Use a Record?

Java records (introduced in Java 14, finalized in Java 16) are perfect for DTOs:

```java
public record ChatRequest(String message) { }
```

This single line automatically gives you:
- **Constructor**: `new ChatRequest("user message")`
- **Accessor**: `request.message()`
- **equals() and hashCode()**: For comparing instances
- **toString()**: For debugging
- **Immutability**: Fields are final and cannot be changed

The equivalent traditional class would be:
```java
public final class ChatRequest {
    private final String message;

    public ChatRequest(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChatRequest that = (ChatRequest) o;
        return Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message);
    }

    @Override
    public String toString() {
        return "ChatRequest[message=" + message + "]";
    }
}
```

**Records save ~20 lines of boilerplate!**

### Compact Constructor for Validation

```java
public record ChatRequest(String message) {
    public ChatRequest {  // This is a "compact constructor"
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
    }
}
```

The compact constructor runs **before** the canonical constructor assigns fields.

**What it validates:**
- **null check**: `message == null` catches missing field
- **blank check**: `message.isBlank()` catches empty strings, whitespace-only strings

**Why throw IllegalArgumentException?**
- Validates at object creation time (fail fast)
- Prevents invalid ChatRequest objects from existing
- Spring will automatically convert this to a 400 Bad Request response

**Example validations:**
```java
new ChatRequest("What's the weather?");     // ✓ Valid
new ChatRequest("");                        // ✗ Throws exception
new ChatRequest("   ");                     // ✗ Throws exception (whitespace)
new ChatRequest(null);                      // ✗ Throws exception
```

### JSON Serialization

Spring Boot automatically handles JSON conversion using Jackson:

**Incoming JSON:**
```json
{
  "message": "What's customer 12345's email?"
}
```

**Automatically deserialized to:**
```java
ChatRequest request = new ChatRequest("What's customer 12345's email?");
```

**How Spring does this:**
1. Receives JSON in HTTP request body
2. Jackson parses JSON
3. Calls record constructor with extracted values
4. Validation runs in compact constructor
5. If successful, the ChatRequest object is passed to your controller method

### Usage in Controller

```java
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
    // request.message() contains the validated user message
    String response = toolOrchestrator.processRequest(request.message());
    return ResponseEntity.ok(new ChatResponse(response));
}
```

**@RequestBody tells Spring:**
- Read the HTTP request body
- Parse it as JSON
- Deserialize into a ChatRequest object
- Validate it (compact constructor runs)
- Pass to the method

## ChatResponse: The AI's Answer

### Code Implementation

```java
package com.techcorp.assistant.module03.dto;

/**
 * Response DTO for chat endpoint.
 *
 * @param response The AI-generated response (potentially tool-augmented)
 * @param toolsUsed Names of tools that were invoked during processing
 */
public record ChatResponse(String response, java.util.List<String> toolsUsed) {
    public ChatResponse(String response) {
        this(response, java.util.List.of());
    }
}
```

### Multiple Constructors

Records can have additional constructors (called "custom constructors"):

```java
public ChatResponse(String response) {
    this(response, java.util.List.of());  // Calls canonical constructor
}
```

This provides two ways to create a ChatResponse:

**With tools:**
```java
new ChatResponse("Customer email is alice@example.com", List.of("getCustomerInfo"))
```

**Without tools (convenience):**
```java
new ChatResponse("Customer email is alice@example.com")
// toolsUsed will be an empty list
```

### The toolsUsed Field

```java
java.util.List<String> toolsUsed
```

**Why include which tools were used?**
- **Debugging**: See what the LLM decided to call
- **Analytics**: Track which tools are used most frequently
- **User transparency**: Show users how their query was processed
- **Cost tracking**: Different tools have different costs (API calls, database queries)

**Example responses:**

**Simple query (no tools):**
```json
{
  "response": "Hello! How can I help you today?",
  "toolsUsed": []
}
```

**Database query (one tool):**
```json
{
  "response": "Customer 12345 (Alice Johnson) can be reached at alice@example.com.",
  "toolsUsed": ["getCustomerInfo"]
}
```

**Complex query (multiple tools):**
```json
{
  "response": "Customer 12345 is Alice Johnson in Boston, where it's currently 18°C and partly cloudy.",
  "toolsUsed": ["getCustomerInfo", "getCurrentWeather"]
}
```

### JSON Serialization

**Outgoing object:**
```java
ChatResponse response = new ChatResponse(
    "Customer 12345's email is alice@example.com",
    List.of("getCustomerInfo")
);
```

**Automatically serialized to:**
```json
{
  "response": "Customer 12345's email is alice@example.com",
  "toolsUsed": ["getCustomerInfo"]
}
```

**How Spring does this:**
1. Your controller returns a ChatResponse object
2. Spring wraps it in ResponseEntity
3. Jackson serializes the record to JSON
4. HTTP response is sent with the JSON body

## Advanced Validation with Bean Validation

For more complex validation, use Jakarta Bean Validation:

```java
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "Message cannot be empty")
    @Size(min = 1, max = 5000, message = "Message must be between 1 and 5000 characters")
    String message
) {}
```

Enable validation in the controller:
```java
@PostMapping("/chat")
public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
    // If validation fails, Spring automatically returns 400 Bad Request
    // with detailed error messages
}
```

**Validation error response:**
```json
{
  "timestamp": "2024-05-06T14:30:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Message must be between 1 and 5000 characters",
  "path": "/api/v1/assistant/chat"
}
```

## Extended ChatResponse with Metadata

For production systems, you might want richer responses:

```java
public record ChatResponse(
    String response,
    List<String> toolsUsed,
    Long processingTimeMs,
    Integer tokenCount,
    String conversationId
) {
    // Convenience constructor for simple cases
    public ChatResponse(String response) {
        this(response, List.of(), null, null, null);
    }
}
```

**Usage:**
```java
long startTime = System.currentTimeMillis();
String aiResponse = toolOrchestrator.processRequest(request.message());
long processingTime = System.currentTimeMillis() - startTime;

return ResponseEntity.ok(new ChatResponse(
    aiResponse,
    List.of("getCustomerInfo"),
    processingTime,
    estimateTokens(aiResponse),
    conversationId
));
```

**Response:**
```json
{
  "response": "Customer 12345's email is alice@example.com",
  "toolsUsed": ["getCustomerInfo"],
  "processingTimeMs": 1542,
  "tokenCount": 87,
  "conversationId": "conv-abc123"
}
```

## Streaming Responses (Alternative Pattern)

For better UX with long responses, consider Server-Sent Events (SSE):

```java
public record ChatStreamChunk(
    String content,
    boolean isComplete,
    List<String> toolsUsed
) {}

@GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
public Flux<ChatStreamChunk> chatStream(@RequestParam String message) {
    return Flux.create(emitter -> {
        // Stream tokens as they arrive from the LLM
        orchestrator.processRequestStreaming(message, token -> {
            emitter.next(new ChatStreamChunk(token, false, List.of()));
        });
        emitter.next(new ChatStreamChunk("", true, toolsUsed));
        emitter.complete();
    });
}
```

Clients receive chunks in real-time:
```
data: {"content":"Customer","isComplete":false,"toolsUsed":[]}
data: {"content":" 12345","isComplete":false,"toolsUsed":[]}
data: {"content":"'s email","isComplete":false,"toolsUsed":[]}
data: {"content":" is","isComplete":false,"toolsUsed":[]}
data: {"content":" alice@example.com","isComplete":false,"toolsUsed":[]}
data: {"content":"","isComplete":true,"toolsUsed":["getCustomerInfo"]}
```

## Design Best Practices

### 1. Keep DTOs Simple and Focused

**Good:**
```java
record ChatRequest(String message) {}
record ChatResponse(String response, List<String> toolsUsed) {}
```

**Avoid:**
```java
record UniversalRequest(String type, String message, Map<String, Object> metadata, ...) {}
// Too generic, loses type safety
```

### 2. Use Records for Immutability

DTOs should be immutable:
- Prevents accidental modification
- Thread-safe by default
- Easier to reason about

### 3. Validate at the Boundary

Validate inputs as early as possible (in the DTO):
```java
public record ChatRequest(String message) {
    public ChatRequest {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be null or empty");
        }
    }
}
```

Don't wait until service layer to validate.

### 4. Include Useful Metadata

**For debugging:**
```java
record ChatResponse(
    String response,
    List<String> toolsUsed,
    String requestId  // Track requests across logs
) {}
```

**For monitoring:**
```java
record ChatResponse(
    String response,
    Long processingTimeMs,  // Performance metrics
    Integer apiCallCount    // Cost tracking
) {}
```

### 5. Version Your DTOs

For evolving APIs:
```java
package com.techcorp.assistant.dto.v1;
record ChatRequest(String message) {}

package com.techcorp.assistant.dto.v2;
record ChatRequest(String message, String context, String language) {}
```

Or use optional fields:
```java
record ChatRequest(
    String message,
    String context,     // Optional in v1, clients can omit
    String language     // Optional in v1, clients can omit
) {}
```

## API Contract Example

With these DTOs, your API contract is clear:

**Request:**
```http
POST /api/v1/assistant/chat HTTP/1.1
Content-Type: application/json

{
  "message": "What's customer 12345's email?"
}
```

**Response:**
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "response": "Customer 12345's email is alice@example.com. Their name is Alice Johnson and they are on the premium subscription plan.",
  "toolsUsed": ["getCustomerInfo"]
}
```

**Error (invalid request):**
```http
HTTP/1.1 400 Bad Request
Content-Type: application/json

{
  "timestamp": "2024-05-06T14:30:00.000+00:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Message cannot be null or empty",
  "path": "/api/v1/assistant/chat"
}
```

## Key Takeaways

- **Records are perfect for DTOs** - concise, immutable, type-safe
- **Compact constructors enable validation** at object creation time
- **ChatRequest encapsulates user input** with validation
- **ChatResponse provides AI output** plus metadata about tool usage
- **Spring handles JSON serialization** automatically with Jackson
- **Validation should happen at the API boundary** for fail-fast behavior
- **Include useful metadata** for debugging, monitoring, and transparency
- **Keep DTOs simple and focused** - one purpose per DTO

## Next Steps: Building the REST Controller

Now that you have clean DTOs defining your API contract, you're ready to build the REST controller that ties everything together.

In the next chapter, **AssistantController**, you'll learn how to:
- Create REST endpoints using Spring annotations
- Wire DTOs and services together
- Handle errors and return appropriate HTTP status codes
- Add health check endpoints
- Document your API

---

**Continue to the next chapter to build the REST API layer!**
