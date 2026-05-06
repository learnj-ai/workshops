# Chapter: DokimosEvaluationService - Systematic RAG Evaluation

## Introduction

**DokimosEvaluationService** orchestrates systematic evaluation of RAG systems using the Dokimos framework. It runs experiments with datasets, tasks, and multiple evaluators to measure quality metrics like faithfulness, hallucination rates, and contextual relevance.

Dokimos provides structured evaluation—moving beyond manual testing to automated, reproducible quality assessment.

## Code

```java
@Service
public class DokimosEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(DokimosEvaluationService.class);

    private final DatasetLoader datasetLoader;
    private final Task ragEvaluationTask;
    private final FaithfulnessEvaluator faithfulnessEvaluator;
    private final HallucinationEvaluator hallucinationEvaluator;
    private final ContextualRelevanceEvaluator contextualRelevanceEvaluator;
    private final ExactMatchEvaluator exactMatchEvaluator;
    private final ResponseLengthEvaluator responseLengthEvaluator;

    public DokimosEvaluationService(
            DatasetLoader datasetLoader,
            Task ragEvaluationTask,
            FaithfulnessEvaluator faithfulnessEvaluator,
            HallucinationEvaluator hallucinationEvaluator,
            ContextualRelevanceEvaluator contextualRelevanceEvaluator,
            ExactMatchEvaluator exactMatchEvaluator,
            ResponseLengthEvaluator responseLengthEvaluator) {
        this.datasetLoader = datasetLoader;
        this.ragEvaluationTask = ragEvaluationTask;
        this.faithfulnessEvaluator = faithfulnessEvaluator;
        this.hallucinationEvaluator = hallucinationEvaluator;
        this.contextualRelevanceEvaluator = contextualRelevanceEvaluator;
        this.exactMatchEvaluator = exactMatchEvaluator;
        this.responseLengthEvaluator = responseLengthEvaluator;
    }

    public ExperimentResult runExperiment(List<String> evaluatorFilter)
            throws DatasetLoader.DatasetLoadException {

        log.info("Starting Dokimos evaluation experiment");

        // Load dataset
        Dataset dataset = datasetLoader.loadDataset();

        // Build evaluator list with optional filtering
        List<Evaluator> evaluators = buildEvaluatorList(evaluatorFilter);

        log.info("Running experiment with {} evaluators on {} examples",
                evaluators.size(), dataset.examples().size());

        // Build and run experiment
        Experiment experiment = Experiment.builder()
                .name("RAG System Evaluation")
                .description("Comprehensive evaluation of RAG system performance")
                .dataset(dataset)
                .task(ragEvaluationTask)
                .evaluators(evaluators)
                .metadata(buildMetadata(evaluatorFilter))
                .build();

        // Execute experiment
        ExperimentResult result = experiment.run();

        // Log summary
        logExperimentSummary(result);

        return result;
    }

    public Map<String, Double> extractAverages(ExperimentResult result) {
        Map<String, Double> averages = new HashMap<>();

        for (Evaluator evaluator : getAllEvaluators()) {
            if (evaluator != null) {
                double avgScore = result.averageScore(evaluator.name());
                averages.put(evaluator.name(), avgScore);
            }
        }

        return averages;
    }

    public String formatForConsole(ExperimentResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================\n");
        sb.append("EXPERIMENT RESULTS\n");
        sb.append("========================================\n");
        sb.append(String.format("Name: %s\n", result.name()));
        sb.append(String.format("Total Examples: %d\n", result.totalCount()));
        sb.append(String.format("Pass Rate: %.2f%%\n", result.passRate() * 100));
        sb.append("\n--- Evaluator Averages ---\n");

        Map<String, Double> averages = extractAverages(result);
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            sb.append(String.format("  %s: %.3f\n", entry.getKey(), entry.getValue()));
        }

        sb.append("========================================\n");
        return sb.toString();
    }

    private List<Evaluator> buildEvaluatorList(List<String> filter) {
        List<Evaluator> evaluators = new ArrayList<>();

        if (shouldInclude("faithfulness", filter)) evaluators.add(faithfulnessEvaluator);
        if (shouldInclude("hallucination", filter)) evaluators.add(hallucinationEvaluator);
        if (shouldInclude("contextual-relevance", filter)) evaluators.add(contextualRelevanceEvaluator);
        if (shouldInclude("exact-match", filter)) evaluators.add(exactMatchEvaluator);
        if (shouldInclude("response-length", filter)) evaluators.add(responseLengthEvaluator);

        return evaluators;
    }

    private boolean shouldInclude(String evaluatorName, List<String> filter) {
        return filter == null || filter.isEmpty() || filter.contains(evaluatorName);
    }

    private void logExperimentSummary(ExperimentResult result) {
        log.info("Experiment completed: {}", result.name());
        log.info("Total examples: {}", result.totalCount());
        log.info("Pass rate: {}", result.passRate());

        Map<String, Double> averages = extractAverages(result);
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            log.info("Average {}: {}", entry.getKey(), entry.getValue());
        }
    }
}
```

## Key Concepts

### Dokimos Framework Components

**Dataset**: Collection of test examples
```java
Dataset dataset = Dataset.builder()
    .name("RAG Test Set")
    .examples(examples)
    .build();

Example example = Example.builder()
    .input("What is the capital of France?")
    .expectedOutput("Paris")
    .build();
```

**Task**: Executes your system for each example
```java
public interface Task {
    Map<String, Object> run(Example example);
}
```

**Evaluator**: Measures quality of outputs
```java
public interface Evaluator {
    EvalResult evaluate(EvalTestCase testCase);
    String name();
    double threshold();
}
```

**Experiment**: Orchestrates dataset + task + evaluators
```java
Experiment experiment = Experiment.builder()
    .dataset(dataset)
    .task(ragEvaluationTask)
    .evaluators(evaluators)
    .build();

ExperimentResult result = experiment.run();
```

### Built-in Evaluators

**FaithfulnessEvaluator**: Checks if response is grounded in context
- Uses LLM to verify claims against context
- Scores 0.0 (unfaithful) to 1.0 (fully faithful)

**HallucinationEvaluator**: Detects fabricated information
- Identifies statements not supported by context
- Lower scores indicate more hallucination

**ContextualRelevanceEvaluator**: Measures context quality
- Evaluates if retrieved context is relevant to query
- Helps diagnose retrieval issues

**ExactMatchEvaluator**: Checks for exact string matches
- Useful for factual questions with known answers
- Binary: 1.0 (match) or 0.0 (no match)

**ResponseLengthEvaluator**: Validates response length
- Ensures responses meet length requirements
- Prevents overly brief or verbose responses

### Experiment Workflow

1. **Load Dataset**: Read test examples from JSON/CSV
2. **Execute Task**: Run RAG query for each example
3. **Run Evaluators**: Apply each evaluator to outputs
4. **Aggregate Results**: Calculate averages, pass rates, ranges
5. **Export Results**: Save to JSON for analysis

## Usage Example

```java
@RestController
@RequestMapping("/api/v1/evaluation")
public class EvaluationController {

    private final DokimosEvaluationService evaluationService;

    @PostMapping("/run")
    public ResponseEntity<ExperimentResultDTO> runEvaluation(
            @RequestBody EvalRequest request) {

        // Run experiment with optional evaluator filter
        ExperimentResult result = evaluationService.runExperiment(
            request.evaluatorFilter()
        );

        // Extract key metrics
        Map<String, Double> averages = evaluationService.extractAverages(result);

        return ResponseEntity.ok(new ExperimentResultDTO(
            result.name(),
            result.totalCount(),
            result.passRate(),
            averages
        ));
    }
}
```

## Configuration

Define evaluator thresholds in `application.properties`:

```properties
# Evaluation thresholds
evaluation.faithfulness.threshold=0.8
evaluation.hallucination.threshold=0.9
evaluation.contextual-relevance.threshold=0.7
evaluation.exact-match.threshold=1.0
evaluation.response-length.min=50
evaluation.response-length.max=500
```

Configure in `@Configuration`:

```java
@Configuration
public class EvaluatorConfig {

    @Bean
    public FaithfulnessEvaluator faithfulnessEvaluator(
            @Value("${evaluation.faithfulness.threshold:0.8}") double threshold,
            ChatModel judgeLLM) {
        return new FaithfulnessEvaluator(judgeLLM, threshold);
    }

    @Bean
    public ResponseLengthEvaluator responseLengthEvaluator(
            @Value("${evaluation.response-length.min:50}") int min,
            @Value("${evaluation.response-length.max:500}") int max) {
        return new ResponseLengthEvaluator(min, max, 1.0);
    }
}
```

## Interpreting Results

### Pass Rate

```
Pass Rate: 85%
```

Percentage of examples that passed all evaluators. Indicates overall system quality.

**Target thresholds**:
- **Development**: 70-80%
- **Staging**: 80-90%
- **Production**: 90%+

### Evaluator Averages

```
faithfulness: 0.92
hallucination: 0.88
contextual-relevance: 0.85
exact-match: 0.65
response-length: 1.0
```

**Interpreting scores**:
- **Faithfulness 0.92**: Responses generally grounded in context (good)
- **Hallucination 0.88**: Some fabrication detected (acceptable)
- **Contextual Relevance 0.85**: Retrieved context mostly relevant (good)
- **Exact Match 0.65**: 65% of factual questions answered correctly (needs improvement)
- **Response Length 1.0**: All responses within length bounds (perfect)

### Identifying Issues

**Low faithfulness + high hallucination**:
- LLM generating information beyond context
- Solution: Stronger system prompt, temperature reduction

**Low contextual relevance**:
- Retrieval returning irrelevant documents
- Solution: Improve chunking, embeddings, or similarity threshold

**Low exact match**:
- Responses correct conceptually but worded differently
- Solution: Use semantic similarity evaluator instead of exact match

## Continuous Evaluation

### Regression Testing

Run evaluations on every code change:

```java
@Test
void shouldMaintainQualityThresholds() {
    ExperimentResult result = evaluationService.runExperiment(null);

    Map<String, Double> averages = evaluationService.extractAverages(result);

    assertThat(averages.get("faithfulness")).isGreaterThan(0.85);
    assertThat(averages.get("hallucination")).isGreaterThan(0.80);
    assertThat(averages.get("contextual-relevance")).isGreaterThan(0.75);
}
```

### A/B Testing

Compare changes against baseline:

```java
public class ABTestingService {

    public ComparisonResult compareExperiments(
            ExperimentResult baseline,
            ExperimentResult variant) {

        Map<String, Double> baselineAvg = extractAverages(baseline);
        Map<String, Double> variantAvg = extractAverages(variant);

        Map<String, Double> deltas = new HashMap<>();
        for (String metric : baselineAvg.keySet()) {
            double delta = variantAvg.get(metric) - baselineAvg.get(metric);
            deltas.put(metric, delta);
        }

        return new ComparisonResult(baselineAvg, variantAvg, deltas);
    }
}
```

### Production Monitoring

Sample live queries for evaluation:

```java
@Component
public class ProductionEvaluationSampler {

    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    public void evaluateProductionSample() {
        // Sample last 24 hours of queries
        List<QueryLog> sample = queryLogRepository.findLast24Hours();

        // Convert to dataset
        Dataset dataset = buildDatasetFromLogs(sample);

        // Run evaluation
        ExperimentResult result = evaluationService.runExperiment(dataset);

        // Alert if quality drops
        if (result.passRate() < 0.85) {
            alertService.sendAlert("RAG quality degraded: " + result.passRate());
        }
    }
}
```

## Best Practices

**Start with small datasets**:
- 10-20 examples for rapid iteration
- Expand to 100+ for comprehensive evaluation
- Balance dataset across query types

**Use multiple evaluators**:
- Each evaluator captures different quality dimensions
- Combine LLM-based and rule-based evaluators
- Monitor correlations between evaluators

**Version your datasets**:
- Track dataset changes in version control
- Maintain separate datasets for different features
- Add examples when bugs are found

**Monitor evaluation costs**:
- LLM-based evaluators make API calls
- Estimate: 2-3x tokens vs original query
- Use cheaper models for evaluation (GPT-3.5 instead of GPT-4)

**Automate evaluation in CI/CD**:
- Run on every PR
- Block deploys if quality drops
- Track quality trends over time

## Key Takeaways

- **DokimosEvaluationService provides systematic RAG quality assessment** beyond manual testing
- **Experiments combine datasets, tasks, and evaluators** for comprehensive evaluation
- **Built-in evaluators measure faithfulness, hallucination, and relevance** with LLM-based scoring
- **Pass rates and averages indicate system quality** and help identify regressions
- **Continuous evaluation in CI/CD prevents quality degradation** in production

## Next Steps

Learn how **RAGEvaluationTask** implements the Task interface to execute RAG queries for evaluation.

---

**Next Chapter**: [09 - RAGEvaluationTask: Task Implementation for Evaluation](./09-rag-evaluation-task.md)
