# Introduction: Building Production-Ready LLM Applications

## Welcome to Enterprise LLM Engineering

Welcome to this hands-on tutorial on taking RAG applications from prototype to production! If you've built LLM applications but struggled with monitoring their behavior, optimizing costs, or validating quality systematically, you're in the right place.

In this tutorial, you'll learn how to instrument, observe, optimize, and evaluate production LLM systems. You'll implement distributed tracing to understand request flows, collect metrics to track performance and costs, build semantic caching to reduce latency and expenses, and use the Dokimos framework to systematically evaluate RAG quality.

This isn't about building your first RAG system—this is about making it production-ready for enterprise deployment.

## Project Overview

### What This Project Does

This project demonstrates **enterprise production patterns** for LLM applications, transforming a basic RAG service into a fully observable, optimized, and validated system ready for production deployment.

Here's what makes it production-ready:

- **Distributed Tracing**: OpenTelemetry integration tracks request flows across service boundaries with span creation, context propagation, and error recording
- **Metrics Collection**: Micrometer integration exposes counters, timers, and gauges for query volume, response times, and token usage
- **Semantic Caching**: Redis-backed caching with embedding-based similarity search reduces costs and improves latency for similar queries
- **Token Optimization**: Smart context selection and prompt compression minimize token usage while maintaining response quality
- **Systematic Evaluation**: Dokimos framework integration enables automated RAG quality assessment with multiple evaluators (faithfulness, hallucination, relevance)
- **Production API Design**: Robust error handling, validation, and observability hooks in REST endpoints

### Why It's Useful

Production LLM applications face unique challenges that don't exist in development:

1. **Observability**: LLM calls are opaque—you need tracing to understand what happened during a request
2. **Cost Management**: Token usage directly impacts your bill—optimization is essential
3. **Quality Assurance**: LLMs are non-deterministic—systematic evaluation prevents regressions
4. **Performance**: Response times matter—caching and optimization reduce latency
5. **Reliability**: Failures happen—proper instrumentation helps you diagnose and recover

This module addresses all these concerns with production-tested patterns.

## Architecture Overview

### How It Works

The system implements a **layered production architecture** with observability, caching, and evaluation integrated at every level:

```mermaid
graph TB
    Client[HTTP Client] -->|POST /api/v1/production/query| Controller[ProductionRAGController]

    Controller -->|@Traced annotation| TracingAspect[RAGTracingAspect]
    Controller -->|Record metrics| Metrics[MetricsCollector]

    Controller -->|Check cache| Cache[CachingService]
    Cache -->|Cache miss| RAG[SimpleRAGService]
    Cache -->|Cache hit| Controller

    RAG -->|Generate response| LLM[ChatModel]
    LLM -->|Response| RAG

    RAG -->|Optimize tokens| Optimizer[TokenOptimizer]

    Controller -->|Store in cache| Cache
    Cache -->|Compute similarity| EmbedModel[EmbeddingModel]
    Cache -->|Store| Redis[(Redis)]

    EvalController[EvaluationController] -->|Run experiment| DokimosService[DokimosEvaluationService]
    DokimosService -->|Load data| DatasetLoader[DatasetLoader]
    DokimosService -->|Execute| Task[RAGEvaluationTask]
    Task -->|Query| RAG
    DokimosService -->|Evaluate| Evaluators[Evaluators]

    Metrics -->|Expose| Prometheus[Prometheus/Actuator]
    TracingAspect -->|Export spans| Telemetry[OpenTelemetry]

    style Controller fill:#e1f5ff
    style Cache fill:#fff4e6
    style Metrics fill:#e8f5e9
    style TracingAspect fill:#f3e5f5
    style DokimosService fill:#fce4ec
    style Optimizer fill:#e3f2fd

    subgraph "Observability Layer"
        Metrics
        TracingAspect
        Telemetry
        Prometheus
    end

    subgraph "Optimization Layer"
        Cache
        Optimizer
        Redis
    end

    subgraph "Evaluation Layer"
        DokimosService
        Task
        Evaluators
        DatasetLoader
    end
```

### Component Flow Explanation

**Request Processing:**
1. Client sends query to `ProductionRAGController`
2. `@Traced` annotation triggers `RAGTracingAspect` to create OpenTelemetry span
3. `MetricsCollector` records query counter and starts response timer
4. `CachingService` checks Redis for semantically similar cached queries
5. On cache miss, `SimpleRAGService` executes RAG pipeline with LLM
6. `TokenOptimizer` selects optimal context segments within token budget
7. Response is cached and metrics recorded (tokens used, response time)

**Evaluation Pipeline:**
8. `EvaluationController` triggers `DokimosEvaluationService` experiment
9. `DatasetLoader` loads test examples from JSON dataset
10. `RAGEvaluationTask` executes RAG queries for each example
11. Multiple evaluators assess quality (faithfulness, hallucination, relevance, citations)
12. Results aggregated with average scores, pass rates, and ranges

**Observability:**
13. `MetricsCollector` exposes Micrometer metrics to Spring Actuator
14. `RAGTracingAspect` exports OpenTelemetry spans to configured backend
15. Logs capture detailed execution information at each layer

## Technical Stack

### Core Technologies

| Technology | Version | Purpose |
|-----------|---------|---------|
| **Spring Boot** | 3.x | Application framework with actuator for metrics |
| **Micrometer** | 1.x | Metrics collection and vendor-neutral instrumentation |
| **OpenTelemetry** | 1.x | Distributed tracing with span creation and context propagation |
| **Redis** | Latest | High-performance caching with semantic similarity search |
| **Dokimos** | 0.0.1 | LLM evaluation framework for RAG quality assessment |
| **LangChain4j** | 1.11.0 | LLM integration and embedding models |

### Key Dependencies

```xml
<!-- Observability - Metrics -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- Observability - Tracing -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-sdk</artifactId>
</dependency>

<!-- Caching -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Evaluation -->
<dependency>
    <groupId>dev.dokimos</groupId>
    <artifactId>dokimos-core</artifactId>
</dependency>

<!-- AOP for tracing -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

### Why These Technologies?

**Micrometer**: Industry-standard metrics library for JVM applications:
- Vendor-neutral abstractions (works with Prometheus, Datadog, New Relic, etc.)
- Spring Boot actuator integration out of the box
- Rich set of meter types (counters, timers, gauges, distribution summaries)
- Dimensional metrics with tag support

**OpenTelemetry**: CNCF standard for observability:
- Vendor-neutral tracing (no lock-in to specific APM tools)
- Context propagation across service boundaries
- Automatic instrumentation for many frameworks
- Unified approach to traces, metrics, and logs

**Redis**: High-performance in-memory data store:
- Millisecond latency for cache lookups
- Native support for hash data structures
- TTL (time-to-live) for automatic cache expiration
- Widely deployed and battle-tested in production

**Dokimos**: Purpose-built LLM evaluation framework:
- Standardized evaluation patterns for RAG systems
- Built-in evaluators for common LLM quality metrics
- Dataset abstraction for consistent testing
- Experiment tracking with detailed results

## What You'll Learn

By completing this tutorial, you will:

- **Implement distributed tracing**: Use OpenTelemetry to track request flows and diagnose issues in production
- **Collect application metrics**: Build counters, timers, and gauges with Micrometer for performance monitoring
- **Build semantic caching**: Implement embedding-based cache lookup to reduce costs and latency
- **Optimize token usage**: Select relevant context and compress prompts to minimize LLM API costs
- **Evaluate RAG quality**: Use Dokimos to systematically assess faithfulness, hallucination, and relevance
- **Create custom evaluators**: Build domain-specific quality checks (e.g., citation verification)
- **Design production APIs**: Implement robust error handling and observability hooks
- **Configure infrastructure**: Set up Redis for caching and Spring Actuator for metrics exposure

### Specific Skills You'll Gain

**Observability:**
- Creating custom OpenTelemetry spans with attributes
- Building aspect-oriented tracing with Spring AOP
- Designing metrics for LLM applications (query volume, response time, token usage)
- Exposing metrics via Spring Boot Actuator

**Optimization:**
- Implementing semantic similarity caching with embeddings
- Token counting and budget management
- Context selection strategies for RAG
- Prompt compression techniques

**Evaluation:**
- Designing evaluation datasets for RAG systems
- Implementing Dokimos tasks and evaluators
- Interpreting faithfulness and hallucination scores
- Building custom rule-based evaluators

**Production Engineering:**
- Error handling strategies for LLM applications
- Cache invalidation and TTL management
- Configuration management for production features
- Testing strategies for non-deterministic systems

## Prerequisites

Before starting this tutorial, you should have:

### Required Knowledge

1. **Java Fundamentals**: Comfortable with Java syntax, OOP, annotations, and lambda expressions
2. **Spring Boot**: Understanding of dependency injection, configuration, REST controllers, and AOP concepts
3. **RAG Basics**: Completed Module 01 or familiar with vector search and RAG architectures
4. **HTTP/REST**: Knowledge of REST API design and HTTP status codes
5. **Basic Redis**: Understanding of key-value stores and caching concepts

### Nice to Have (But Not Required)

- Experience with **observability tools** (Prometheus, Grafana, Jaeger)
- Knowledge of **LLM evaluation metrics** (we'll explain them in detail)
- Familiarity with **aspect-oriented programming** (AOP)
- Understanding of **semantic similarity** and **cosine distance**

### Development Environment

You'll need:

- **Java 17 or higher** installed
- **Maven 3.6+** for building the project
- **Redis** running locally (or Docker to run Redis container)
- **IDE** with Java support (IntelliJ IDEA recommended)
- **curl** or **Postman** for testing REST endpoints
- **Git** for cloning the repository

### System Requirements

- **RAM**: 8GB minimum (Redis + application + embedding model)
- **Disk Space**: ~1GB for dependencies and models
- **OS**: Windows, macOS, or Linux
- **Network**: Internet connection for downloading dependencies and LLM API access (if using remote models)

---

## Ready to Begin?

In the next chapters, you'll:

1. **Implement metrics collection** with Micrometer and expose them via Spring Actuator
2. **Build distributed tracing** with OpenTelemetry and aspect-oriented programming
3. **Create semantic caching** with Redis and embedding-based similarity
4. **Optimize token usage** with context selection and prompt compression
5. **Set up Dokimos evaluation** with datasets, tasks, and evaluators
6. **Build custom evaluators** for domain-specific quality checks
7. **Deploy the production API** with full observability and error handling

Let's make your RAG application production-ready!

---

**Next Chapter**: [02 - MetricsCollector: Monitoring Performance and Costs](./02-metrics-collector.md)
