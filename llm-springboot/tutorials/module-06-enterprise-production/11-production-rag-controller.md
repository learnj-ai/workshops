# Chapter: ProductionRAGController - Production-Ready API

## Introduction

**ProductionRAGController** integrates all production features—observability, caching, and metrics—into a robust REST API. It demonstrates how to build production-grade endpoints with proper error handling, tracing, and performance monitoring.

## Code

```java
@RestController
@RequestMapping("/api/v1/production")
public class ProductionRAGController {

    private static final Logger log = LoggerFactory.getLogger(ProductionRAGController.class);

    private final SimpleRAGService ragService;
    private final CachingService cachingService;
    private final MetricsCollector metricsCollector;

    @Value("${semantic-cache.enabled:true}")
    private boolean semanticCacheEnabled;

    public ProductionRAGController(
            SimpleRAGService ragService,
            CachingService cachingService,
            MetricsCollector metricsCollector) {
        this.ragService = ragService;
        this.cachingService = cachingService;
        this.metricsCollector = metricsCollector;
    }

    @PostMapping("/query")
    @Traced("rag.query")
    public ResponseEntity<RAGResponse> query(@RequestBody QueryRequest request) {
        log.info("Received production query: {}", request.query());

        // Record metrics
        metricsCollector.recordQuery();
        Timer.Sample sample = metricsCollector.startTimer();

        try {
            // Check semantic cache
            if (semanticCacheEnabled) {
                String cachedResponse = cachingService.semanticCacheGet(request.query());
                if (cachedResponse != null) {
                    metricsCollector.recordResponseTime(sample);
                    return ResponseEntity.ok(new RAGResponse(cachedResponse, true));
                }
            }

            // Execute RAG query
            SimpleRAGService.RAGResponse response = ragService.query(request.query());

            // Cache the response
            if (semanticCacheEnabled) {
                cachingService.semanticCachePut(request.query(), response.response());
            }

            // Record token usage (estimated)
            metricsCollector.recordTokens(estimateTokens(request.query(), response.response()));

            // Record response time
            metricsCollector.recordResponseTime(sample);

            return ResponseEntity.ok(new RAGResponse(response.response(), false));

        } catch (Exception e) {
            log.error("Error processing query", e);
            metricsCollector.recordResponseTime(sample);
            return ResponseEntity.internalServerError()
                    .body(new RAGResponse("An error occurred processing your request.", false));
        }
    }

    private int estimateTokens(String query, String response) {
        int words = (query + response).split("\\s+").length;
        return (int) Math.ceil(words / 0.75);
    }

    public record QueryRequest(String query) {}

    public record RAGResponse(String response, boolean fromCache) {}
}
```

## Key Concepts

### Production Patterns Integration

The controller integrates three critical patterns:

**1. Observability** (`@Traced` annotation):
- Creates OpenTelemetry span for the request
- Captures timing, errors, and context
- Enables distributed tracing

**2. Metrics** (MetricsCollector):
- Records query count, response time, token usage
- Exposes to Prometheus/Grafana
- Enables performance monitoring

**3. Caching** (CachingService):
- Checks semantic cache before LLM call
- Stores responses for future queries
- Reduces costs and improves latency

### Request Flow

```
1. Request arrives → @Traced creates span
2. metricsCollector.recordQuery() → increment counter
3. sample = metricsCollector.startTimer() → start timing
4. Check cache → if hit, return immediately
5. Cache miss → execute RAG service
6. Cache response → store for future queries
7. recordTokens() → track usage
8. recordResponseTime() → stop timer
9. Return response → client receives result
```

### Error Handling

```java
try {
    // Happy path
} catch (Exception e) {
    log.error("Error processing query", e);
    metricsCollector.recordResponseTime(sample);  // Still record timing
    return ResponseEntity.internalServerError()
            .body(new RAGResponse("An error occurred...", false));
}
```

**Why record metrics in error path?**
- Failed requests still consume resources
- Timing failures helps diagnose issues
- Error rate is a key metric

### Feature Toggles

```java
@Value("${semantic-cache.enabled:true}")
private boolean semanticCacheEnabled;

if (semanticCacheEnabled) {
    // Use cache
}
```

**Benefits**:
- Enable/disable features without code changes
- A/B test performance impact
- Quick rollback if issues arise

## Configuration

```properties
# Feature toggles
semantic-cache.enabled=true

# Cache settings
semantic-cache.similarity-threshold=0.95
semantic-cache.ttl-seconds=3600

# Observability
management.endpoints.web.exposure.include=health,metrics,prometheus
management.tracing.enabled=true

# Application info
spring.application.name=rag-production-service
```

## Testing

### Integration Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class ProductionRAGControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SimpleRAGService ragService;

    @Test
    void shouldReturnRAGResponse() throws Exception {
        // Given
        when(ragService.query("What is Java?"))
            .thenReturn(new RAGResponse("Java is a programming language", List.of()));

        // When/Then
        mockMvc.perform(post("/api/v1/production/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"What is Java?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.response").value("Java is a programming language"))
            .andExpect(jsonPath("$.fromCache").value(false));
    }

    @Test
    void shouldReturnCachedResponse() throws Exception {
        // Given
        when(cachingService.semanticCacheGet("What is Java?"))
            .thenReturn("Java is a programming language");

        // When/Then
        mockMvc.perform(post("/api/v1/production/query")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"query\":\"What is Java?\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fromCache").value(true));

        // Verify RAG service never called
        verify(ragService, never()).query(any());
    }
}
```

### Load Testing

```bash
# Using Apache Bench
ab -n 1000 -c 10 -p query.json -T application/json \
   http://localhost:8080/api/v1/production/query

# query.json
{"query":"What is Java?"}
```

Monitor metrics during load test:
- Response time percentiles (p50, p95, p99)
- Cache hit rate
- Token usage rate
- Error rate

## Advanced Patterns

### Request Validation

```java
@PostMapping("/query")
public ResponseEntity<RAGResponse> query(
        @Valid @RequestBody QueryRequest request) {
    // ...
}

public record QueryRequest(
    @NotBlank(message = "Query cannot be empty")
    @Size(max = 1000, message = "Query too long")
    String query
) {}
```

### Rate Limiting

```java
@Service
public class RateLimiter {

    private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();

    public boolean allowRequest(String clientId) {
        AtomicInteger count = requestCounts.computeIfAbsent(clientId, k -> new AtomicInteger(0));

        if (count.get() >= 100) {  // 100 requests per minute
            return false;
        }

        count.incrementAndGet();
        return true;
    }

    @Scheduled(fixedRate = 60000)  // Reset every minute
    public void resetCounts() {
        requestCounts.clear();
    }
}

@PostMapping("/query")
public ResponseEntity<RAGResponse> query(
        @RequestBody QueryRequest request,
        @RequestHeader("X-Client-ID") String clientId) {

    if (!rateLimiter.allowRequest(clientId)) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new RAGResponse("Rate limit exceeded", false));
    }

    // Process request
}
```

### Circuit Breaker

```java
@Service
public class CircuitBreakerRAGService {

    private final CircuitBreaker circuitBreaker = CircuitBreaker.ofDefaults("rag");
    private final SimpleRAGService ragService;

    public RAGResponse query(String query) {
        return circuitBreaker.executeSupplier(() -> ragService.query(query));
    }
}
```

### Async Processing

For long-running queries:

```java
@PostMapping("/query/async")
public ResponseEntity<AsyncQueryResponse> queryAsync(@RequestBody QueryRequest request) {
    String requestId = UUID.randomUUID().toString();

    CompletableFuture.supplyAsync(() -> {
        SimpleRAGService.RAGResponse response = ragService.query(request.query());
        responseStore.put(requestId, response);
        return response;
    });

    return ResponseEntity.accepted()
        .body(new AsyncQueryResponse(requestId, "Processing"));
}

@GetMapping("/query/async/{requestId}")
public ResponseEntity<RAGResponse> getAsyncResult(@PathVariable String requestId) {
    SimpleRAGService.RAGResponse response = responseStore.get(requestId);

    if (response == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new RAGResponse("Query not found", false));
    }

    return ResponseEntity.ok(new RAGResponse(response.response(), false));
}
```

## Deployment Considerations

### Health Checks

Spring Actuator provides `/actuator/health`:

```json
{
  "status": "UP",
  "components": {
    "redis": {"status": "UP"},
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### Kubernetes Probes

```yaml
apiVersion: v1
kind: Pod
metadata:
  name: rag-service
spec:
  containers:
  - name: rag-service
    image: rag-service:1.0
    livenessProbe:
      httpGet:
        path: /actuator/health/liveness
        port: 8080
      initialDelaySeconds: 30
      periodSeconds: 10
    readinessProbe:
      httpGet:
        path: /actuator/health/readiness
        port: 8080
      initialDelaySeconds: 10
      periodSeconds: 5
```

### Logging

Structured logging for production:

```java
@PostMapping("/query")
public ResponseEntity<RAGResponse> query(@RequestBody QueryRequest request) {
    MDC.put("queryLength", String.valueOf(request.query().length()));
    MDC.put("requestId", UUID.randomUUID().toString());

    try {
        log.info("Processing query", kv("query", request.query()));
        // ...
    } finally {
        MDC.clear();
    }
}
```

## Best Practices

**Always record metrics for failures**:
- Failed requests still consume resources
- Helps identify bottlenecks and issues

**Use feature toggles for new patterns**:
- Enable gradual rollout
- Quick rollback if needed

**Validate input at API boundary**:
- Prevent invalid requests early
- Clear error messages for clients

**Log strategically**:
- INFO: Request received, cache hit/miss
- ERROR: Failures with stack traces
- DEBUG: Detailed execution flow

**Monitor key metrics**:
- Request rate, latency percentiles
- Cache hit rate, token usage
- Error rate by type

## Key Takeaways

- **ProductionRAGController integrates observability, caching, and metrics** into a production-ready API
- **@Traced annotation enables distributed tracing** without cluttering business logic
- **Metrics recorded for both success and failure paths** provide complete visibility
- **Semantic caching checked before expensive LLM calls** reduces costs and latency
- **Feature toggles enable safe rollout** of new production patterns

## Conclusion

You've completed the Module 06 tutorial! You now understand how to build production-ready LLM applications with:

- Comprehensive observability (metrics and tracing)
- Cost optimization (caching and token management)
- Quality assurance (systematic evaluation)
- Robust error handling and monitoring

These patterns form the foundation of enterprise LLM deployments.

---

**Congratulations on completing Module 06: Enterprise Production!**
