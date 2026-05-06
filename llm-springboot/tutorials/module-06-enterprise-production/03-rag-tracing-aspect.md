# Chapter: RAGTracingAspect - Distributed Tracing with OpenTelemetry

## Introduction

**RAGTracingAspect** implements distributed tracing using OpenTelemetry and Spring AOP. It automatically wraps annotated methods with trace spans, capturing execution flow, timing, and errors without cluttering your business logic.

This component demonstrates how to build cross-cutting concerns with aspect-oriented programming while integrating with industry-standard observability tools.

## Code

```java
@Aspect
@Component
public class RAGTracingAspect {

    private static final Logger log = LoggerFactory.getLogger(RAGTracingAspect.class);
    private final Tracer tracer;

    public RAGTracingAspect(Tracer tracer) {
        this.tracer = tracer;
    }

    @Around("@annotation(traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint, Traced traced) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String methodName = signature.getName();
        String className = signature.getDeclaringType().getSimpleName();

        // Create span name from annotation value or method name
        String spanName = traced.value().isEmpty() ?
                className + "." + methodName :
                traced.value();

        Span span = tracer.spanBuilder(spanName)
                .setAttribute("component", className)
                .setAttribute("method", methodName)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            log.debug("Starting traced method: {}", spanName);

            Object result = joinPoint.proceed();

            span.setStatus(StatusCode.OK);
            return result;

        } catch (Throwable throwable) {
            log.error("Error in traced method: {}", spanName, throwable);

            span.setStatus(StatusCode.ERROR, throwable.getMessage());
            span.recordException(throwable);

            throw throwable;

        } finally {
            span.end();
        }
    }
}
```

## Supporting Components

### @Traced Annotation

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Traced {
    String value() default "";
}
```

Simple marker annotation with optional span name override.

### TracingConfig

```java
@Configuration
public class TracingConfig {

    @Value("${spring.application.name}")
    private String applicationName;

    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault()
                .merge(Resource.create(
                        Attributes.of(
                                ResourceAttributes.SERVICE_NAME, applicationName
                        )
                ));

        SpanExporter logExporter = new LoggingSpanExporter();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(logExporter))
                .setResource(resource)
                .build();

        return OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(applicationName, "1.0.0");
    }
}
```

## Key Concepts

### Aspect-Oriented Programming (AOP)

**@Aspect**: Marks this class as an aspect that applies cross-cutting concerns

**@Around**: Intercepts method execution and wraps it with custom logic
- Runs before and after the target method
- Can modify arguments, return values, or suppress exceptions
- Must call `joinPoint.proceed()` to execute the target method

**Pointcut Expression**: `@annotation(traced)`
- Matches any method annotated with `@Traced`
- Binds the annotation instance to the `traced` parameter
- Enables reading annotation attributes (like custom span names)

### OpenTelemetry Tracing

**Span**: Represents a unit of work with timing information
- Start time, end time, duration
- Status (OK, ERROR, UNSET)
- Attributes (key-value pairs for context)
- Events and exceptions

**Scope**: Makes a span the "current" span for this thread
- Enables context propagation to child spans
- Automatically manages thread-local storage
- Must be closed (try-with-resources) to prevent leaks

**Context Propagation**:
- Child spans automatically link to the current parent
- Traces can span multiple services (distributed tracing)
- HTTP headers carry trace context across network boundaries

### Span Lifecycle

```java
// 1. Create span
Span span = tracer.spanBuilder("operation.name")
    .setAttribute("key", "value")
    .startSpan();

// 2. Make it current
try (Scope scope = span.makeCurrent()) {
    // 3. Do work (child spans will link to this)
    doWork();

    // 4. Mark success
    span.setStatus(StatusCode.OK);

} catch (Exception e) {
    // 5. Record errors
    span.setStatus(StatusCode.ERROR, e.getMessage());
    span.recordException(e);
    throw e;

} finally {
    // 6. Always end the span
    span.end();
}
```

## Usage Example

Annotate any method to enable tracing:

```java
@RestController
@RequestMapping("/api/v1/production")
public class ProductionRAGController {

    @PostMapping("/query")
    @Traced("rag.query")  // Custom span name
    public ResponseEntity<RAGResponse> query(@RequestBody QueryRequest request) {
        // Method body automatically wrapped in a span
        // Errors automatically recorded
        // Timing automatically captured
        return ResponseEntity.ok(ragService.query(request.query()));
    }
}
```

Without custom name (uses class + method):

```java
@Traced  // Span name: "MyService.processData"
public void processData(String data) {
    // ...
}
```

## What Gets Captured

For each traced method call, you get:

**Span Attributes**:
- `component`: Class name (e.g., "ProductionRAGController")
- `method`: Method name (e.g., "query")
- Any custom attributes you add

**Timing**:
- Start timestamp
- End timestamp
- Duration (calculated automatically)

**Status**:
- `OK`: Method completed successfully
- `ERROR`: Exception was thrown

**Exceptions**:
- Exception type, message, and stack trace
- Linked to the span for error analysis

**Parent-Child Relationships**:
- If called from another traced method, forms parent-child link
- Enables visualizing the entire request flow

## Trace Visualization

When you run a traced request, you might see:

```
Span: rag.query (ProductionRAGController.query)
├─ Duration: 245ms
├─ Status: OK
├─ Attributes:
│  ├─ component: ProductionRAGController
│  └─ method: query
└─ Child Spans:
   ├─ Span: cache.lookup
   │  ├─ Duration: 12ms
   │  └─ Status: OK
   └─ Span: rag.execute
      ├─ Duration: 220ms
      └─ Status: OK
```

## Integration with Observability Platforms

### Jaeger

Configure Jaeger exporter in production:

```java
@Bean
public OpenTelemetry openTelemetry() {
    JaegerGrpcSpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder()
        .setEndpoint("http://jaeger:14250")
        .build();

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(jaegerExporter).build())
        .setResource(resource)
        .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();
}
```

### Zipkin

```java
ZipkinSpanExporter zipkinExporter = ZipkinSpanExporter.builder()
    .setEndpoint("http://zipkin:9411/api/v2/spans")
    .build();
```

### OTLP (OpenTelemetry Protocol)

```java
OtlpGrpcSpanExporter otlpExporter = OtlpGrpcSpanExporter.builder()
    .setEndpoint("http://otel-collector:4317")
    .build();
```

## Best Practices

**Use meaningful span names**:
- Good: `"rag.query"`, `"cache.semantic_lookup"`, `"llm.completion"`
- Bad: `"method1"`, `"doStuff"`, `"process"`

**Add relevant attributes**:
```java
span.setAttribute("query.length", query.length());
span.setAttribute("cache.hit", cacheHit);
span.setAttribute("model", "gpt-4");
```

**Don't over-trace**:
- Trace significant operations (API calls, database queries, LLM calls)
- Skip trivial utility methods
- Too many spans increases overhead and storage costs

**Always end spans**:
- Use try-with-resources for Scope
- Use try-finally for Span
- Unended spans leak memory

**Record exceptions properly**:
```java
span.recordException(exception);  // Captures type, message, stack trace
span.setStatus(StatusCode.ERROR, exception.getMessage());
```

## Performance Considerations

**Overhead**:
- Span creation: ~1-5 microseconds
- Attribute addition: ~100 nanoseconds per attribute
- Export: Batched in background thread (minimal impact)

**Sampling**:
For high-volume applications, sample traces:

```java
.setSampler(Sampler.traceIdRatioBased(0.1))  // Sample 10% of traces
```

**Batch Export**:
Use `BatchSpanProcessor` instead of `SimpleSpanProcessor` in production:
- Buffers spans in memory
- Exports in batches (reduces network calls)
- Configurable batch size and schedule

## Testing

```java
@Test
void shouldCreateSpanForTracedMethod() {
    InMemorySpanExporter exporter = InMemorySpanExporter.create();
    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(SimpleSpanProcessor.create(exporter))
        .build();

    Tracer tracer = tracerProvider.get("test");
    RAGTracingAspect aspect = new RAGTracingAspect(tracer);

    // Execute traced method via aspect
    // ...

    // Verify span created
    List<SpanData> spans = exporter.getFinishedSpanItems();
    assertThat(spans).hasSize(1);
    assertThat(spans.get(0).getName()).isEqualTo("rag.query");
}
```

## Key Takeaways

- **AOP separates observability from business logic** making code cleaner and easier to maintain
- **OpenTelemetry provides vendor-neutral tracing** that works with Jaeger, Zipkin, and commercial APM tools
- **Spans capture timing, context, and errors** giving you complete visibility into request execution
- **Context propagation enables distributed tracing** across service boundaries
- **@Traced annotation makes tracing declarative** requiring minimal code changes

## Next Steps

Learn how **TracingConfig** sets up the OpenTelemetry infrastructure and how to customize span exporters for your observability platform.

---

**Next Chapter**: [04 - TracingConfig: OpenTelemetry Infrastructure Setup](./04-tracing-config.md)
