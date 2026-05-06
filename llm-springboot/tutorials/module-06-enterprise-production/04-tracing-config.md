# Chapter: TracingConfig - OpenTelemetry Infrastructure Setup

## Introduction

**TracingConfig** establishes the OpenTelemetry infrastructure for your application. It configures the tracer provider, span exporters, resource attributes, and creates the tracer bean that gets injected into your aspects and services.

This component demonstrates how to bootstrap observability infrastructure in Spring Boot applications.

## Code

```java
@Configuration
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

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

        // Use logging exporter for demonstration
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

### Resource

**Resource** represents the entity producing telemetry:

```java
Resource resource = Resource.getDefault()
    .merge(Resource.create(
        Attributes.of(
            ResourceAttributes.SERVICE_NAME, applicationName
        )
    ));
```

**Why it matters**:
- Identifies your service in distributed traces
- Enables filtering traces by service in observability platforms
- Can include additional metadata (version, environment, host)

**Common resource attributes**:
```java
Attributes.of(
    ResourceAttributes.SERVICE_NAME, "rag-service",
    ResourceAttributes.SERVICE_VERSION, "1.2.3",
    ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production",
    ResourceAttributes.HOST_NAME, "app-server-01"
)
```

### Span Exporter

**SpanExporter** sends completed spans to an observability backend:

**LoggingSpanExporter** (development):
- Writes spans to application logs
- No external dependencies
- Great for local debugging
- Not suitable for production (log volume)

**Production exporters**:
```java
// Jaeger
JaegerGrpcSpanExporter.builder()
    .setEndpoint("http://jaeger:14250")
    .build()

// Zipkin
ZipkinSpanExporter.builder()
    .setEndpoint("http://zipkin:9411/api/v2/spans")
    .build()

// OTLP (OpenTelemetry Collector)
OtlpGrpcSpanExporter.builder()
    .setEndpoint("http://otel-collector:4317")
    .build()
```

### Span Processor

**SpanProcessor** controls when and how spans are exported:

**SimpleSpanProcessor**:
- Exports each span immediately when it ends
- Good for development (immediate visibility)
- High overhead for production (blocks on export)

```java
SimpleSpanProcessor.create(exporter)
```

**BatchSpanProcessor** (production):
- Buffers spans in memory
- Exports in batches on schedule or when buffer fills
- Configurable batch size, queue size, export interval

```java
BatchSpanProcessor.builder(exporter)
    .setMaxQueueSize(2048)           // Buffer up to 2048 spans
    .setMaxExportBatchSize(512)      // Export 512 at a time
    .setScheduleDelay(Duration.ofSeconds(5))  // Export every 5 seconds
    .build()
```

### Tracer

**Tracer** creates spans:

```java
@Bean
public Tracer tracer(OpenTelemetry openTelemetry) {
    return openTelemetry.getTracer(applicationName, "1.0.0");
}
```

Parameters:
- **Instrumentation name**: Typically your application name
- **Version**: Your application version (useful for correlating traces with deployments)

## Configuration Patterns

### Development Configuration

```java
@Configuration
@Profile("dev")
public class DevTracingConfig {

    @Bean
    public OpenTelemetry openTelemetry(@Value("${spring.application.name}") String appName) {
        Resource resource = Resource.create(
            Attributes.of(ResourceAttributes.SERVICE_NAME, appName)
        );

        SpanExporter consoleExporter = new LoggingSpanExporter();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(SimpleSpanProcessor.create(consoleExporter))
            .setResource(resource)
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}
```

### Production Configuration

```java
@Configuration
@Profile("prod")
public class ProdTracingConfig {

    @Value("${tracing.jaeger.endpoint}")
    private String jaegerEndpoint;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${spring.application.version}")
    private String applicationVersion;

    @Bean
    public OpenTelemetry openTelemetry() {
        Resource resource = Resource.getDefault().merge(
            Resource.create(
                Attributes.of(
                    ResourceAttributes.SERVICE_NAME, applicationName,
                    ResourceAttributes.SERVICE_VERSION, applicationVersion,
                    ResourceAttributes.DEPLOYMENT_ENVIRONMENT, "production"
                )
            )
        );

        SpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder()
            .setEndpoint(jaegerEndpoint)
            .setTimeout(Duration.ofSeconds(2))
            .build();

        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(
                BatchSpanProcessor.builder(jaegerExporter)
                    .setMaxQueueSize(4096)
                    .setMaxExportBatchSize(512)
                    .setScheduleDelay(Duration.ofSeconds(5))
                    .build()
            )
            .setResource(resource)
            .setSampler(Sampler.traceIdRatioBased(0.1))  // 10% sampling
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }

    @Bean
    public Tracer tracer(OpenTelemetry openTelemetry) {
        return openTelemetry.getTracer(applicationName, applicationVersion);
    }
}
```

## Multiple Exporters

Export to multiple backends simultaneously:

```java
@Bean
public OpenTelemetry openTelemetry() {
    Resource resource = buildResource();

    // Export to both Jaeger and console
    SpanExporter jaegerExporter = JaegerGrpcSpanExporter.builder()
        .setEndpoint("http://jaeger:14250")
        .build();

    SpanExporter consoleExporter = new LoggingSpanExporter();

    SpanExporter multiExporter = SpanExporter.composite(jaegerExporter, consoleExporter);

    SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
        .addSpanProcessor(BatchSpanProcessor.builder(multiExporter).build())
        .setResource(resource)
        .build();

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .build();
}
```

## Sampling Strategies

### Probability-Based Sampling

Sample a percentage of traces:

```java
.setSampler(Sampler.traceIdRatioBased(0.1))  // 10% of traces
```

### Parent-Based Sampling

Always sample if parent was sampled (maintains trace integrity):

```java
.setSampler(Sampler.parentBased(Sampler.traceIdRatioBased(0.1)))
```

### Always On/Off

```java
.setSampler(Sampler.alwaysOn())   // Sample everything (dev)
.setSampler(Sampler.alwaysOff())  // Sample nothing (disabled)
```

### Custom Sampling

Sample based on attributes:

```java
public class CustomSampler implements Sampler {
    @Override
    public SamplingResult shouldSample(
            Context parentContext,
            String traceId,
            String name,
            SpanKind spanKind,
            Attributes attributes,
            List<LinkData> parentLinks) {

        // Always sample errors
        if (name.contains("error")) {
            return SamplingResult.recordAndSample();
        }

        // Sample 10% of normal traffic
        return traceId.hashCode() % 10 == 0 ?
            SamplingResult.recordAndSample() :
            SamplingResult.drop();
    }
}
```

## Environment-Based Configuration

Use `application.properties` for environment-specific settings:

```properties
# application.properties
spring.application.name=rag-service

# application-dev.properties
tracing.enabled=true
tracing.exporter=console
tracing.sampling.ratio=1.0

# application-prod.properties
tracing.enabled=true
tracing.exporter=jaeger
tracing.jaeger.endpoint=http://jaeger-collector:14250
tracing.sampling.ratio=0.1
```

Configuration class:

```java
@Configuration
@ConditionalOnProperty(name = "tracing.enabled", havingValue = "true")
public class TracingConfig {

    @Value("${tracing.exporter}")
    private String exporterType;

    @Value("${tracing.sampling.ratio}")
    private double samplingRatio;

    @Bean
    public SpanExporter spanExporter() {
        return switch (exporterType) {
            case "jaeger" -> createJaegerExporter();
            case "zipkin" -> createZipkinExporter();
            case "console" -> new LoggingSpanExporter();
            default -> throw new IllegalArgumentException("Unknown exporter: " + exporterType);
        };
    }

    @Bean
    public Sampler sampler() {
        return Sampler.traceIdRatioBased(samplingRatio);
    }
}
```

## Graceful Shutdown

Ensure spans are flushed on application shutdown:

```java
@Configuration
public class TracingConfig {

    @Bean
    public OpenTelemetry openTelemetry() {
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build();

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }
}
```

Or use Spring's `@PreDestroy`:

```java
@Configuration
public class TracingConfig {

    private SdkTracerProvider tracerProvider;

    @Bean
    public OpenTelemetry openTelemetry() {
        this.tracerProvider = SdkTracerProvider.builder()
            .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
            .setResource(resource)
            .build();

        return OpenTelemetrySdk.builder()
            .setTracerProvider(tracerProvider)
            .build();
    }

    @PreDestroy
    public void shutdown() throws InterruptedException {
        if (tracerProvider != null) {
            tracerProvider.shutdown().join(10, TimeUnit.SECONDS);
        }
    }
}
```

## Best Practices

**Use profiles for environment-specific configuration**:
- Development: console exporter, 100% sampling, simple processor
- Production: remote exporter, percentage sampling, batch processor

**Set meaningful resource attributes**:
- Include service name, version, environment
- Helps filter and aggregate traces in observability platforms

**Configure appropriate sampling**:
- Development: 100% sampling
- Production: 1-10% sampling (adjust based on traffic volume)
- Always sample errors (custom sampler)

**Use batch processor in production**:
- Reduces export overhead
- Configure based on traffic volume and latency requirements

**Handle shutdown gracefully**:
- Flush remaining spans before application exits
- Prevents losing traces for in-flight requests

## Testing

```java
@Test
void shouldConfigureTracerProvider() {
    TracingConfig config = new TracingConfig();
    ReflectionTestUtils.setField(config, "applicationName", "test-service");

    OpenTelemetry otel = config.openTelemetry();
    Tracer tracer = config.tracer(otel);

    assertThat(tracer).isNotNull();

    // Create a test span
    Span span = tracer.spanBuilder("test").startSpan();
    span.end();

    // Verify span was exported (depends on exporter implementation)
}
```

## Key Takeaways

- **TracingConfig bootstraps OpenTelemetry infrastructure** by configuring resources, exporters, and processors
- **Resource attributes identify your service** in distributed traces across observability platforms
- **Span processors control export behavior**—use SimpleSpanProcessor for dev, BatchSpanProcessor for production
- **Sampling reduces overhead** while maintaining trace coverage for errors and important requests
- **Profile-based configuration** enables different settings for development and production environments

## Next Steps

Learn how **CachingService** implements semantic caching with Redis to reduce costs and improve latency.

---

**Next Chapter**: [05 - CachingService: Semantic Caching with Redis](./05-caching-service.md)
