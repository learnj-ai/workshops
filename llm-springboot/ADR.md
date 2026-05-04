# Architecture Decision Records

This document tracks all significant architectural decisions made during the development of the LLM Spring Boot Workshop (Modules 03-06).

## ADR-001: Use Langchain4J ChatModel Interface

**Date**: 2025-01-15

**Status**: Accepted

**Context**:
Langchain4J 1.11.0 changed from `ChatLanguageModel` to `ChatModel` interface. This affects all modules that interact with LLMs.

**Decision**:
Use `ChatModel` interface with `.chat()` method instead of deprecated `ChatLanguageModel` with `.generate()`.

**Consequences**:
- **Positive**: Aligns with latest Langchain4J API
- **Positive**: Simpler, more intuitive method naming
- **Negative**: Requires migration from older tutorials
- **Mitigation**: Clear documentation in troubleshooting guide

**Implementation**:
```java
import dev.langchain4j.model.chat.ChatModel;

ChatModel chatModel = OpenAiChatModel.builder()
    .apiKey(apiKey)
    .modelName("gpt-4")
    .build();

String response = chatModel.chat(prompt);
```

## ADR-002: Module Isolation with Independent Ports

**Date**: 2025-01-16

**Status**: Accepted

**Context**:
Multiple modules need to run simultaneously for testing and demonstration purposes.

**Decision**:
Each module runs on a distinct port (8081-8086) and can operate independently without interfering with other modules.

**Consequences**:
- **Positive**: Modules can run concurrently
- **Positive**: Easier testing and development
- **Positive**: Clear module boundaries
- **Negative**: More ports to manage
- **Mitigation**: Document port assignments in README

**Port Allocation**:
- Module 01: 8081
- Module 02: 8082
- Module 03: 8083
- Module 04: 8084
- Module 05: 8085
- Module 06: 8086

## ADR-003: PostgreSQL for Module 03 Tool Data

**Date**: 2025-01-17

**Status**: Accepted

**Context**:
Module 03 demonstrates database tool integration. Need a production-grade database for realistic examples.

**Decision**:
Use PostgreSQL 16 with Docker initialization scripts for schema and seed data.

**Consequences**:
- **Positive**: Production-ready database
- **Positive**: Automated schema initialization
- **Positive**: Standard SQL syntax
- **Negative**: Requires Docker infrastructure
- **Mitigation**: Docker Compose orchestration handles startup

**Implementation**:
```yaml
postgres:
  image: postgres:16-alpine
  volumes:
    - ./schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
    - ./data.sql:/docker-entrypoint-initdb.d/02-data.sql
```

## ADR-004: Redis for Agent Memory and Caching

**Date**: 2025-01-18

**Status**: Accepted

**Context**:
Module 04 needs stateful conversation memory, Module 06 needs semantic caching.

**Decision**:
Use Redis 7 for both conversation memory (Module 04) and semantic caching (Module 06).

**Consequences**:
- **Positive**: Fast in-memory storage
- **Positive**: TTL support for memory expiration
- **Positive**: Shared infrastructure across modules
- **Negative**: Requires external service
- **Mitigation**: Docker Compose manages Redis lifecycle

**TTL Configuration**:
- Conversation memory: 24 hours
- Semantic cache: 1 hour (configurable)

## ADR-005: ReAct Pattern for Autonomous Agents

**Date**: 2025-01-19

**Status**: Accepted

**Context**:
Module 04 demonstrates autonomous agents that can reason and act iteratively.

**Decision**:
Implement ReAct (Reasoning + Acting) pattern with explicit thought/action/observation loop.

**Consequences**:
- **Positive**: Industry-standard agent pattern
- **Positive**: Transparent reasoning process
- **Positive**: Supports multi-step problem solving
- **Negative**: Higher token consumption
- **Mitigation**: Configurable max iterations (default: 5)

**Flow**:
1. THOUGHT: LLM reasons about next step
2. ACTION: LLM selects and invokes tool
3. OBSERVATION: Tool result fed back to LLM
4. Repeat until FINAL ANSWER

## ADR-006: Multi-Layer Security Architecture

**Date**: 2025-01-20

**Status**: Accepted

**Context**:
Module 05 demonstrates production security practices for LLM applications.

**Decision**:
Implement defense-in-depth with 6 sequential security layers:
1. Prompt injection detection
2. Input PII masking
3. Access control on retrieved data
4. Output validation (LLM-as-judge)
5. Hallucination detection
6. Output PII masking

**Consequences**:
- **Positive**: Comprehensive security coverage
- **Positive**: Multiple failure points prevent attacks
- **Positive**: Complete audit trail
- **Negative**: Increased latency (~1-2s overhead)
- **Mitigation**: Layer-specific optimization, async processing

## ADR-007: LLM-as-Judge for Output Validation

**Date**: 2025-01-21

**Status**: Accepted

**Context**:
Need to validate AI outputs for safety, toxicity, and hallucinations.

**Decision**:
Use separate GPT-3.5-turbo instance with temperature=0.0 as validation judge.

**Consequences**:
- **Positive**: Flexible validation criteria
- **Positive**: Leverages LLM understanding
- **Positive**: Can explain violations
- **Negative**: Additional API cost
- **Negative**: Validation adds latency
- **Mitigation**: Use faster/cheaper model for validation

**Validation Criteria**:
- Toxicity and harmful content
- Confidential information disclosure
- Professional tone
- Hallucination detection

## ADR-008: OpenTelemetry for Distributed Tracing

**Date**: 2025-01-22

**Status**: Accepted

**Context**:
Module 06 demonstrates production observability practices.

**Decision**:
Use OpenTelemetry SDK with custom aspect-based instrumentation via `@Traced` annotation.

**Consequences**:
- **Positive**: Industry-standard tracing
- **Positive**: Vendor-neutral format
- **Positive**: Easy to switch exporters (Jaeger, Zipkin)
- **Negative**: Requires additional dependencies
- **Mitigation**: Minimal configuration, simple logging exporter for workshop

**Implementation**:
```java
@Traced("operation-name")
public void method() {
    // Automatically creates span with context propagation
}
```

## ADR-009: Prometheus + Grafana for Metrics

**Date**: 2025-01-23

**Status**: Accepted

**Context**:
Module 06 needs production-grade metrics collection and visualization.

**Decision**:
Use Micrometer with Prometheus registry, visualize in Grafana dashboards.

**Consequences**:
- **Positive**: Industry-standard metrics stack
- **Positive**: Rich visualization capabilities
- **Positive**: Spring Boot Actuator integration
- **Negative**: Requires additional services
- **Mitigation**: Docker Compose handles infrastructure

**Metrics Collected**:
- `rag.queries.total` (Counter)
- `rag.response.time` (Timer with quantiles)
- `rag.tokens.used` (Gauge)

## ADR-010: Semantic Caching with Embedding Similarity

**Date**: 2025-01-24

**Status**: Accepted

**Context**:
Exact-match caching misses semantically similar queries. Need intelligent caching.

**Decision**:
Implement semantic caching using embedding cosine similarity with 0.95 threshold.

**Consequences**:
- **Positive**: Higher cache hit rate
- **Positive**: Handles paraphrased queries
- **Negative**: Embedding computation overhead
- **Mitigation**: In-memory embedding cache to avoid recomputation

**Cache Strategy**:
1. Compute query embedding
2. Compare with cached query embeddings
3. If similarity ≥ 0.95, return cached response
4. Otherwise, execute RAG and cache result

## ADR-011: Token Budget Management

**Date**: 2025-01-25

**Status**: Accepted

**Context**:
Long context windows increase cost and latency. Need intelligent context selection.

**Decision**:
Implement token optimizer with relevance-based segment selection within configurable budget.

**Consequences**:
- **Positive**: Reduced token consumption
- **Positive**: Lower API costs
- **Positive**: Faster responses
- **Negative**: Risk of missing relevant context
- **Mitigation**: Configurable budget, relevance scoring

**Strategy**:
1. Tokenize all retrieved segments
2. Rank by relevance score
3. Select highest-scoring segments within budget
4. Compress prompt (remove filler words)

## ADR-012: Custom Evaluation Framework

**Date**: 2025-01-26

**Status**: Accepted

**Context**:
Module 06 demonstrates evaluation practices for RAG systems.

**Decision**:
Implement custom evaluation with three metrics:
- Accuracy (embedding cosine similarity)
- Relevance (response-to-context similarity)
- Faithfulness (LLM-as-judge scoring)

**Consequences**:
- **Positive**: Concrete implementation example
- **Positive**: Demonstrates multiple evaluation approaches
- **Negative**: Simpler than production frameworks (e.g., Dokimos)
- **Note**: Can be replaced with Dokimos framework (see alternative change)

**Metrics**:
```java
double accuracy = cosineSimilarity(actual, expected);
double relevance = avgSimilarity(response, contexts);
double faithfulness = llmJudgeScore(response, sources);
```

## ADR-013: OpenShift Deployment Configuration

**Date**: 2025-01-27

**Status**: Accepted

**Context**:
Module 06 targets enterprise deployments on OpenShift/Kubernetes.

**Decision**:
Provide full deployment manifests with:
- 3-replica Deployment for high availability
- Resource limits (1-2Gi memory, 500m-1CPU)
- Health probes (liveness 60s, readiness 30s)
- TLS edge termination via Route
- Secrets for API keys

**Consequences**:
- **Positive**: Production-ready configuration
- **Positive**: High availability setup
- **Positive**: Secure credential management
- **Negative**: Requires OpenShift/K8s cluster
- **Mitigation**: Can run locally with Docker for development

## ADR-014: Simplified RAG for Modules 05-06

**Date**: 2025-01-28

**Status**: Accepted

**Context**:
Modules 05-06 focus on security and production features, not RAG complexity.

**Decision**:
Use simplified RAG service with mock document retrieval instead of full vector search.

**Consequences**:
- **Positive**: Focuses on security/production features
- **Positive**: Reduces dependencies
- **Positive**: Faster to understand
- **Negative**: Not production-grade RAG
- **Note**: Module 02 has full RAG implementation

**Rationale**:
Modules 05-06 demonstrate security layers and production practices that are orthogonal to RAG complexity. Simplified RAG reduces cognitive load.

## ADR-015: UBI9 OpenJDK 21 Base Image

**Date**: 2025-01-29

**Status**: Accepted

**Context**:
Need container base image for OpenShift deployment.

**Decision**:
Use `registry.access.redhat.com/ubi9/openjdk-21` as Dockerfile base.

**Consequences**:
- **Positive**: Red Hat enterprise support
- **Positive**: Security scanning and updates
- **Positive**: OpenShift compatibility
- **Negative**: Larger image size than Alpine
- **Trade-off**: Enterprise support > image size

**Dockerfile**:
```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21:latest
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

## Summary Table

| ADR | Decision | Module(s) | Status |
|-----|----------|-----------|--------|
| 001 | ChatModel interface | All | Accepted |
| 002 | Module isolation (ports 8081-8086) | All | Accepted |
| 003 | PostgreSQL for tools | 03 | Accepted |
| 004 | Redis for memory/cache | 04, 06 | Accepted |
| 005 | ReAct pattern | 04 | Accepted |
| 006 | Multi-layer security | 05 | Accepted |
| 007 | LLM-as-judge validation | 05, 06 | Accepted |
| 008 | OpenTelemetry tracing | 06 | Accepted |
| 009 | Prometheus + Grafana | 06 | Accepted |
| 010 | Semantic caching | 06 | Accepted |
| 011 | Token budget management | 06 | Accepted |
| 012 | Custom evaluation framework | 06 | Accepted |
| 013 | OpenShift deployment | 06 | Accepted |
| 014 | Simplified RAG | 05, 06 | Accepted |
| 015 | UBI9 base image | 06 | Accepted |

## Future Considerations

- **ADR-016 Candidate**: Replace custom evaluation (ADR-012) with Dokimos framework
  - See OpenSpec change: `integrate-dokimos-evaluation`
  - Trade-off: Production-grade evaluators vs custom implementation transparency

- **ADR-017 Candidate**: Add streaming response support
  - Use Server-Sent Events for incremental responses
  - Improves perceived latency for long-running queries

- **ADR-018 Candidate**: Implement circuit breaker for OpenAI API
  - Use Resilience4j for fault tolerance
  - Prevents cascading failures during API outages

## References

- [Langchain4J Documentation](https://docs.langchain4j.dev/)
- [Spring Boot 4.0.5 Reference](https://docs.spring.io/spring-boot/reference/)
- [OpenTelemetry Java](https://opentelemetry.io/docs/languages/java/)
- [Prometheus Best Practices](https://prometheus.io/docs/practices/)
- [OpenShift Container Platform](https://docs.openshift.com/)
