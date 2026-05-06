# Chapter: RAGEvaluationTask - Task Implementation for Evaluation

## Introduction

**RAGEvaluationTask** implements Dokimos's `Task` interface, bridging your RAG service with the evaluation framework. It executes RAG queries for each dataset example and structures outputs for evaluators to assess.

## Code

```java
public class RAGEvaluationTask implements Task {

    private static final Logger log = LoggerFactory.getLogger(RAGEvaluationTask.class);

    private final SimpleRAGService ragService;

    public RAGEvaluationTask(SimpleRAGService ragService) {
        this.ragService = ragService;
    }

    @Override
    public Map<String, Object> run(Example example) {
        String query = example.input();

        if (query == null || query.isBlank()) {
            log.warn("Empty query in example, returning empty output");
            return Map.of(
                    "output", "",
                    "context", "",
                    "error", "Empty or null query"
            );
        }

        try {
            log.debug("Executing RAG query: {}", query);

            // Execute RAG service query
            SimpleRAGService.RAGResponse response = ragService.query(query);

            // Build context from source documents for evaluators
            String context = String.join("\n\n", response.sourceDocuments());

            // Return outputs for evaluation
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("output", response.response());
            outputs.put("context", context);
            outputs.put("source_count", response.sourceDocuments().size());

            log.debug("RAG query completed successfully");
            return outputs;

        } catch (Exception e) {
            log.error("Error executing RAG query: {}", query, e);

            // Return error information for evaluation tracking
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("output", "");
            outputs.put("context", "");
            outputs.put("error", e.getMessage());
            outputs.put("error_type", e.getClass().getSimpleName());

            return outputs;
        }
    }
}
```

## Key Concepts

### Task Interface

Dokimos's `Task` interface has one method:

```java
public interface Task {
    Map<String, Object> run(Example example);
}
```

**Contract**:
- **Input**: `Example` with input query and optional expected output
- **Output**: `Map<String, Object>` with keys that evaluators expect

**Standard output keys**:
- `"output"`: The system's response (required for most evaluators)
- `"context"`: Retrieved context/documents (required for RAG evaluators)
- `"error"`: Error message if execution failed (optional)

### Error Handling

```java
try {
    // Execute RAG
    return successfulOutputs;
} catch (Exception e) {
    // Return error outputs instead of throwing
    return Map.of(
        "output", "",
        "context", "",
        "error", e.getMessage()
    );
}
```

**Why return errors instead of throwing?**
- Experiment continues even if some examples fail
- Errors tracked in results for analysis
- Helps identify systemic vs isolated failures

### Output Structure

Different evaluators expect different output keys:

**FaithfulnessEvaluator** needs:
```java
outputs.put("output", response);        // Response to check
outputs.put("context", retrievedDocs);  // Context to verify against
```

**ExactMatchEvaluator** needs:
```java
outputs.put("output", response);  // Compare against example.expectedOutput()
```

**ContextualRelevanceEvaluator** needs:
```java
outputs.put("context", retrievedDocs);  // Context to evaluate
```

## Configuration

Register as a Spring bean:

```java
@Configuration
public class TaskConfig {

    @Bean
    public Task ragEvaluationTask(SimpleRAGService ragService) {
        return new RAGEvaluationTask(ragService);
    }
}
```

## Usage Example

Dokimos framework calls this automatically:

```java
// In DokimosEvaluationService
Experiment experiment = Experiment.builder()
    .dataset(dataset)
    .task(ragEvaluationTask)  // RAGEvaluationTask instance
    .evaluators(evaluators)
    .build();

ExperimentResult result = experiment.run();
```

**Execution flow**:
1. Experiment iterates through dataset examples
2. For each example, calls `ragEvaluationTask.run(example)`
3. Task executes RAG query and returns outputs
4. Each evaluator receives outputs and assesses quality
5. Results aggregated into `ExperimentResult`

## Advanced Patterns

### Adding Metadata

Include additional information for analysis:

```java
public Map<String, Object> run(Example example) {
    long startTime = System.currentTimeMillis();

    SimpleRAGService.RAGResponse response = ragService.query(example.input());

    long latency = System.currentTimeMillis() - startTime;

    Map<String, Object> outputs = new HashMap<>();
    outputs.put("output", response.response());
    outputs.put("context", String.join("\n\n", response.sourceDocuments()));

    // Metadata for analysis
    outputs.put("latency_ms", latency);
    outputs.put("source_count", response.sourceDocuments().size());
    outputs.put("query_length", example.input().length());

    return outputs;
}
```

### Retry Logic

Retry transient failures:

```java
public Map<String, Object> run(Example example) {
    int maxRetries = 3;
    Exception lastException = null;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            return executeQuery(example);
        } catch (TransientException e) {
            lastException = e;
            log.warn("Attempt {}/{} failed: {}", attempt, maxRetries, e.getMessage());
            sleep(attempt * 1000);  // Exponential backoff
        }
    }

    // All retries failed
    return Map.of(
        "output", "",
        "error", "Failed after " + maxRetries + " attempts: " + lastException.getMessage()
    );
}
```

### Variant Testing

Test different RAG strategies in one experiment:

```java
public class MultiStrategyRAGTask implements Task {

    private final Map<String, RAGService> strategies;

    @Override
    public Map<String, Object> run(Example example) {
        Map<String, Object> outputs = new HashMap<>();

        for (Map.Entry<String, RAGService> entry : strategies.entrySet()) {
            String strategy = entry.getKey();
            RAGService service = entry.getValue();

            try {
                RAGResponse response = service.query(example.input());
                outputs.put("output_" + strategy, response.response());
                outputs.put("context_" + strategy, response.context());
            } catch (Exception e) {
                outputs.put("error_" + strategy, e.getMessage());
            }
        }

        return outputs;
    }
}
```

Then evaluate each strategy:

```java
FaithfulnessEvaluator baselineEval = new FaithfulnessEvaluator(
    llm, 0.8, "output_baseline", "context_baseline"
);

FaithfulnessEvaluator optimizedEval = new FaithfulnessEvaluator(
    llm, 0.8, "output_optimized", "context_optimized"
);
```

## Testing

```java
@Test
void shouldExecuteRAGQuerySuccessfully() {
    // Given
    SimpleRAGService mockRagService = mock(SimpleRAGService.class);
    when(mockRagService.query("What is Java?"))
        .thenReturn(new RAGResponse(
            "Java is a programming language",
            List.of("Java docs", "Wikipedia")
        ));

    RAGEvaluationTask task = new RAGEvaluationTask(mockRagService);

    Example example = Example.builder()
        .input("What is Java?")
        .expectedOutput("Java is a programming language")
        .build();

    // When
    Map<String, Object> outputs = task.run(example);

    // Then
    assertThat(outputs).containsKeys("output", "context", "source_count");
    assertThat(outputs.get("output")).isEqualTo("Java is a programming language");
    assertThat(outputs.get("source_count")).isEqualTo(2);
}

@Test
void shouldHandleErrors() {
    // Given
    SimpleRAGService mockRagService = mock(SimpleRAGService.class);
    when(mockRagService.query(any()))
        .thenThrow(new RuntimeException("LLM API error"));

    RAGEvaluationTask task = new RAGEvaluationTask(mockRagService);

    Example example = Example.builder()
        .input("What is Java?")
        .build();

    // When
    Map<String, Object> outputs = task.run(example);

    // Then
    assertThat(outputs).containsKey("error");
    assertThat(outputs.get("error")).isEqualTo("LLM API error");
    assertThat(outputs.get("output")).isEqualTo("");
}
```

## Best Practices

**Return structured outputs consistently**:
- Use same keys across all examples
- Evaluators depend on specific keys
- Document expected output format

**Handle errors gracefully**:
- Don't throw exceptions from `run()`
- Return error information in outputs map
- Log errors for debugging

**Include context for RAG evaluators**:
- FaithfulnessEvaluator needs retrieved documents
- ContextualRelevanceEvaluator needs context
- Concatenate documents with clear separators

**Log execution details**:
- Debug logs for each query
- Warn logs for errors
- Include query text in logs

**Keep task focused**:
- Task should only execute the system
- Don't implement evaluation logic in task
- Leave assessment to evaluators

## Key Takeaways

- **RAGEvaluationTask bridges RAG service with Dokimos framework** by implementing the Task interface
- **Tasks execute the system and return structured outputs** for evaluators to assess
- **Error handling returns error information** instead of throwing exceptions to allow experiments to continue
- **Output structure must match evaluator expectations** with keys like "output" and "context"
- **Tasks should be focused on execution** leaving quality assessment to evaluators

## Next Steps

Learn how **CitationQualityEvaluator** implements custom evaluation logic for domain-specific quality checks.

---

**Next Chapter**: [10 - CitationQualityEvaluator: Custom Evaluator Implementation](./10-citation-quality-evaluator.md)
