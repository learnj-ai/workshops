# RAG Controller: REST API

## Overview

`RAGController` exposes the RAG pipeline as REST endpoints. It provides two main operations:

1. **/query** - Execute the full RAG pipeline and get an answer
2. **/compare** - Compare vector, keyword, and hybrid search results

This is the entry point for client applications to interact with your RAG system.

## The RAGController

```java
package com.techcorp.assistant.rag;

import dev.langchain4j.data.segment.TextSegment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.concurrent.StructuredTaskScope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
public class RAGController {

    private final RAGService ragService;
    private final HybridSearchService hybridSearchService;

    public RAGController(RAGService ragService, HybridSearchService hybridSearchService) {
        this.ragService = ragService;
        this.hybridSearchService = hybridSearchService;
    }

    @PostMapping("/query")
    public ResponseEntity<RAGResponse> query(@Valid @RequestBody RAGRequest request) {
        String answer = ragService.query(request.question(), request.useQueryExpansion());
        return ResponseEntity.ok(new RAGResponse(answer));
    }

    @PostMapping("/compare")
    public ResponseEntity<SearchComparisonResponse> compareSearchMethods(
            @Valid @RequestBody CompareRequest request) {
        try (var scope = StructuredTaskScope.open(StructuredTaskScope.Joiner.<List<String>>allSuccessfulOrThrow())) {
            var vectorTask = scope.fork(() -> hybridSearchService.vectorOnlySearch(request.query(), request.topK())
                    .stream()
                    .map(TextSegment::text)
                    .toList());

            var keywordTask = scope.fork(() -> hybridSearchService.keywordOnlySearch(request.query(), request.topK())
                    .stream()
                    .map(TextSegment::text)
                    .toList());

            var hybridTask = scope.fork(() -> hybridSearchService.hybridSearch(request.query(), request.topK())
                    .stream()
                    .map(TextSegment::text)
                    .toList());

            scope.join();

            return ResponseEntity.ok(new SearchComparisonResponse(
                    request.query(),
                    vectorTask.get(),
                    keywordTask.get(),
                    hybridTask.get()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Search comparison interrupted", e);
        }
    }

    record CompareRequest(
            @NotBlank String query,
            @Min(1) @Max(20) int topK) {

        CompareRequest {
            topK = (topK == 0) ? 5 : topK;
        }
    }
}
```

## Endpoint 1: POST /api/v1/rag/query

### Request

```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "How do I reset my password?",
    "useQueryExpansion": true
  }'
```

### Response

```json
{
  "answer": "To reset your password, go to the login page and click 'Forgot Password'. Enter your email address and you'll receive a reset link valid for 24 hours. Follow the link and enter your new password twice to confirm. Passwords must be at least 12 characters and include uppercase, lowercase, numbers, and special characters."
}
```

### What Happens

1. Spring Boot validates the request (`@Valid`)
2. `ragService.query()` executes the full pipeline
3. Answer is wrapped in `RAGResponse` and returned as JSON

## Endpoint 2: POST /api/v1/rag/compare

### Request

```bash
curl -X POST http://localhost:8080/api/v1/rag/compare \
  -H "Content-Type: application/json" \
  -d '{
    "query": "VPN setup",
    "topK": 3
  }'
```

### Response

```json
{
  "query": "VPN setup",
  "vectorResults": [
    "To connect remotely, use the company VPN. Download the VPN client...",
    "Remote access requires authentication via VPN. Install the client...",
    "For remote work, establish a secure connection using the virtual..."
  ],
  "keywordResults": [
    "VPN setup instructions: 1. Download VPN client 2. Enter credentials...",
    "The VPN configuration requires admin approval. Submit a ticket...",
    "VPN troubleshooting: If VPN connection fails, check firewall..."
  ],
  "hybridResults": [
    "VPN setup instructions: 1. Download VPN client 2. Enter credentials...",
    "To connect remotely, use the company VPN. Download the VPN client...",
    "For remote work, establish a secure connection using the virtual..."
  ]
}
```

### Structured Concurrency (Java 21+)

```java
try (var scope = StructuredTaskScope.open(...)) {
    var vectorTask = scope.fork(() -> hybridSearchService.vectorOnlySearch(...));
    var keywordTask = scope.fork(() -> hybridSearchService.keywordOnlySearch(...));
    var hybridTask = scope.fork(() -> hybridSearchService.hybridSearch(...));

    scope.join();

    return ResponseEntity.ok(new SearchComparisonResponse(
        request.query(),
        vectorTask.get(),
        keywordTask.get(),
        hybridTask.get()));
}
```

**Why structured concurrency?**

- **Parallel execution**: All three searches run simultaneously
- **Automatic cleanup**: Tasks are scoped to the try block
- **Failure handling**: If any task fails, all are cancelled
- **Safety**: No thread leaks

**Performance gain:**
- Sequential: 200ms + 150ms + 250ms = 600ms
- Parallel: max(200ms, 150ms, 250ms) = 250ms
- 2.4x speedup

## Validation with Jakarta Bean Validation

### RAGRequest Validation

```java
public record RAGRequest(
        @NotBlank String question,
        Boolean useQueryExpansion) {
    // ...
}
```

**@NotBlank on question:**
- Cannot be null
- Cannot be empty ("")
- Cannot be only whitespace ("   ")

**Invalid request:**

```bash
curl -X POST .../query -d '{"question": "", "useQueryExpansion": true}'
```

**Response:**

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "question: must not be blank"
}
```

Spring Boot's validation framework automatically returns HTTP 400 with details.

### CompareRequest Validation

```java
record CompareRequest(
        @NotBlank String query,
        @Min(1) @Max(20) int topK) {

    CompareRequest {
        topK = (topK == 0) ? 5 : topK;
    }
}
```

**@Min(1) @Max(20) on topK:**
- Must be at least 1
- Cannot exceed 20 (prevents excessive results)

**Invalid request:**

```bash
curl -X POST .../compare -d '{"query": "test", "topK": 50}'
```

**Response:**

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "topK: must be less than or equal to 20"
}
```

## Error Handling

Global exception handling (from Module 01) applies:

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(MethodArgumentNotValidException ex) {
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        return ResponseEntity.badRequest().body(new ErrorResponse(400, "Validation failed", errors));
    }
}
```

This catches validation errors and returns clean error messages to clients.

## Testing the Controller

### Unit Test: /query Endpoint

```java
@WebMvcTest(RAGController.class)
class RAGControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RAGService ragService;

    @MockBean
    private HybridSearchService hybridSearchService;

    @Test
    void testQueryEndpoint() throws Exception {
        when(ragService.query(anyString(), anyBoolean()))
            .thenReturn("Password reset instructions...");

        mockMvc.perform(post("/api/v1/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "How do I reset my password?",
                      "useQueryExpansion": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.answer").value("Password reset instructions..."));
    }

    @Test
    void testValidationFailsOnBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/rag/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "question": "",
                      "useQueryExpansion": true
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
```

### Integration Test: /compare Endpoint

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class RAGControllerIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void testCompareEndpoint() {
        CompareRequest request = new CompareRequest("VPN setup", 3);

        ResponseEntity<SearchComparisonResponse> response = restTemplate.postForEntity(
            "/api/v1/rag/compare",
            request,
            SearchComparisonResponse.class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("VPN setup", response.getBody().query());
        assertEquals(3, response.getBody().vectorResults().size());
        assertEquals(3, response.getBody().keywordResults().size());
        assertEquals(3, response.getBody().hybridResults().size());
    }
}
```

## Key Takeaways

1. **Two endpoints**: /query for RAG, /compare for evaluation
2. **Structured concurrency**: Parallel search execution in Java 21+
3. **Validation**: Jakarta Bean Validation for input checking
4. **Clean API**: Simple request/response DTOs
5. **Error handling**: Global exception handler for consistent errors

---

**Next Chapter**: [11 - Conclusion and Next Steps](./11-conclusion.md)
