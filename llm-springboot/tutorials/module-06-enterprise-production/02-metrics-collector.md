# Chapter: MetricsCollector - Monitoring Performance and Costs

## Introduction

**MetricsCollector** is your window into production system behavior. Using Micrometer, it tracks query volume, response times, and token usage—the three critical metrics for understanding LLM application performance and costs.

This component demonstrates how to instrument Spring Boot applications with custom metrics that integrate seamlessly with monitoring systems like Prometheus, Datadog, or New Relic.

## Code

```java
@Service
public class MetricsCollector {

    private final Counter queryCounter;
    private final Timer responseTimer;
    private final AtomicLong tokensUsed;

    public MetricsCollector(MeterRegistry meterRegistry) {
        // Counter for total queries
        this.queryCounter = Counter.builder("rag.queries.total")
                .description("Total number of RAG queries processed")
                .tag("type", "rag")
                .register(meterRegistry);

        // Timer for response time
        this.responseTimer = Timer.builder("rag.response.time")
                .description("RAG query response time in milliseconds")
                .tag("type", "rag")
                .register(meterRegistry);

        // Gauge for token usage
        this.tokensUsed = new AtomicLong(0);
        Gauge.builder("rag.tokens.used", tokensUsed, AtomicLong::get)
                .description("Total tokens used by RAG queries")
                .tag("type", "rag")
                .register(meterRegistry);
    }

    public void recordQuery() {
        queryCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start();
    }

    public void recordResponseTime(Timer.Sample sample) {
        sample.stop(responseTimer);
    }

    public void recordTokens(long tokens) {
        tokensUsed.addAndGet(tokens);
    }
}
```

## Key Concepts

### Micrometer Meter Types

**Counter** (`rag.queries.total`):
- Monotonically increasing value
- Never decreases
- Perfect for counting events (queries processed)
- Use `.increment()` to add 1, or `.increment(n)` for other amounts

**Timer** (`rag.response.time`):
- Measures duration of events
- Automatically captures count, total time, max time, percentiles
- Use `Timer.start()` to begin, `sample.stop(timer)` to finish
- Records both the event count and latency distribution

**Gauge** (`rag.tokens.used`):
- Reports current value of something
- Can increase or decrease
- Polls the value when scraped (doesn't store history)
- Perfect for tracking cumulative token usage

### Tags and Dimensions

```java
.tag("type", "rag")
```

Tags enable **dimensional metrics**—you can filter and aggregate by tag values:
- Monitor RAG queries separately from other query types
- Compare performance across different models or strategies
- Track costs per customer, region, or feature

### Why This Design?

**Constructor injection of `MeterRegistry`**:
- Spring Boot auto-configures the registry based on your classpath
- No vendor lock-in—works with any Micrometer-compatible backend
- Testable with `SimpleMeterRegistry` in unit tests

**Separate methods for each metric**:
- Clear, focused API for callers
- Each metric type has appropriate method signatures
- Easy to mock in tests

**AtomicLong for gauge**:
- Thread-safe accumulation of token counts
- Gauge reads current value without synchronization overhead
- Survives across multiple requests

## Usage Example

From `ProductionRAGController`:

```java
@PostMapping("/query")
public ResponseEntity<RAGResponse> query(@RequestBody QueryRequest request) {
    // Record that a query started
    metricsCollector.recordQuery();

    // Start timing
    Timer.Sample sample = metricsCollector.startTimer();

    try {
        // Execute RAG query
        SimpleRAGService.RAGResponse response = ragService.query(request.query());

        // Record token usage
        int tokens = estimateTokens(request.query(), response.response());
        metricsCollector.recordTokens(tokens);

        // Record response time
        metricsCollector.recordResponseTime(sample);

        return ResponseEntity.ok(new RAGResponse(response.response(), false));

    } catch (Exception e) {
        // Still record timing for failed requests
        metricsCollector.recordResponseTime(sample);
        throw e;
    }
}
```

## Exposed Metrics

When you access `/actuator/prometheus` endpoint, you'll see:

```prometheus
# HELP rag_queries_total Total number of RAG queries processed
# TYPE rag_queries_total counter
rag_queries_total{type="rag"} 1247.0

# HELP rag_response_time_seconds RAG query response time in milliseconds
# TYPE rag_response_time_seconds summary
rag_response_time_seconds_count{type="rag"} 1247.0
rag_response_time_seconds_sum{type="rag"} 3842.195
rag_response_time_seconds_max{type="rag"} 8.451

# HELP rag_tokens_used Total tokens used by RAG queries
# TYPE rag_tokens_used gauge
rag_tokens_used{type="rag"} 453891.0
```

## Integration with Monitoring Systems

### Prometheus

Add to `application.properties`:

```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

Configure Prometheus to scrape:

```yaml
scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
```

### Grafana Dashboards

Use these metrics to build dashboards:

- **Request Rate**: `rate(rag_queries_total[5m])`
- **Average Latency**: `rate(rag_response_time_seconds_sum[5m]) / rate(rag_response_time_seconds_count[5m])`
- **p95 Latency**: `histogram_quantile(0.95, rate(rag_response_time_seconds_bucket[5m]))`
- **Tokens Per Second**: `rate(rag_tokens_used[5m])`

## Best Practices

**Always record timing for failures**:
- Put `recordResponseTime()` in `finally` block or catch clause
- Failed requests still consume resources and impact SLAs

**Use consistent naming**:
- Follow Micrometer conventions: `domain.metric.unit`
- Use dots (`.`) not underscores in metric names
- Prometheus automatically converts to underscores

**Tag thoughtfully**:
- High cardinality tags (user IDs) can overwhelm monitoring systems
- Good tags: environment, region, model, strategy
- Bad tags: query text, customer ID, timestamp

**Monitor costs, not just performance**:
- Token usage directly correlates to LLM API bills
- Track tokens per request type to identify cost optimization opportunities
- Alert on unusual token consumption spikes

## Testing

```java
@Test
void shouldRecordMetrics() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    MetricsCollector collector = new MetricsCollector(registry);

    // Record a query
    collector.recordQuery();
    collector.recordTokens(1500);

    // Verify counter
    Counter counter = registry.counter("rag.queries.total", "type", "rag");
    assertThat(counter.count()).isEqualTo(1.0);

    // Verify gauge
    Gauge gauge = registry.gauge("rag.tokens.used", Tags.of("type", "rag"));
    assertThat(gauge.value()).isEqualTo(1500.0);
}
```

## Key Takeaways

- **Micrometer provides vendor-neutral metrics** that work with any monitoring backend
- **Different meter types serve different purposes**: counters for events, timers for latency, gauges for current values
- **Tags enable powerful filtering and aggregation** in monitoring queries
- **MetricsCollector centralizes instrumentation** making it easy to maintain consistent metrics across your application
- **Token usage tracking is essential** for understanding and controlling LLM costs

## Next Steps

Learn how **RAGTracingAspect** provides distributed tracing with OpenTelemetry to understand request flows in production.

---

**Next Chapter**: [03 - RAGTracingAspect: Distributed Tracing with OpenTelemetry](./03-rag-tracing-aspect.md)
