# Chapter: CitationQualityEvaluator - Custom Evaluator Implementation

## Introduction

**CitationQualityEvaluator** demonstrates how to build custom evaluators for domain-specific quality requirements. It uses pattern matching to verify that RAG responses include proper citations in `[1]`, `[2]` format, ensuring transparency about information sources.

## Code

```java
public class CitationQualityEvaluator extends BaseEvaluator {

    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\d+\\]");
    private final int minCitations;

    public CitationQualityEvaluator(int minCitations, double threshold) {
        super("citation-quality", threshold, List.of(EvalTestCaseParam.ACTUAL_OUTPUT));
        this.minCitations = minCitations;
    }

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String response = testCase.actualOutput();

        // Handle null or empty responses
        if (response == null || response.isBlank()) {
            return EvalResult.of(
               name(),
               0.0,
               threshold(),
               "Response is missing or empty"
            );
        }

        // Count citations using pattern matching
        Matcher matcher = CITATION_PATTERN.matcher(response);
        int citationCount = 0;
        while (matcher.find()) {
            citationCount++;
        }

        // Binary scoring: 1.0 if meets minimum, 0.0 otherwise
        double score = citationCount >= minCitations ? 1.0 : 0.0;

        String reason = String.format(
            "Found %d citations (minimum required: %d)%s",
            citationCount,
            minCitations,
            citationCount >= minCitations ? " - Meets threshold" : " - Below threshold"
        );

        return EvalResult.of(name(), score, threshold(), reason);
    }
}
```

## Key Concepts

### BaseEvaluator

Dokimos provides `BaseEvaluator` abstract class:

```java
public abstract class BaseEvaluator implements Evaluator {
    protected BaseEvaluator(
        String name,
        double threshold,
        List<EvalTestCaseParam> requiredParams
    ) {
        this.name = name;
        this.threshold = threshold;
        this.requiredParams = requiredParams;
    }

    protected abstract EvalResult runEvaluation(EvalTestCase testCase);
}
```

**Benefits**:
- Handles common evaluator setup
- Validates required parameters exist
- Standardizes evaluation flow

### Required Parameters

```java
List.of(EvalTestCaseParam.ACTUAL_OUTPUT)
```

**EvalTestCaseParam options**:
- `ACTUAL_OUTPUT`: The system's response (from task outputs["output"])
- `EXPECTED_OUTPUT`: The ground truth (from example.expectedOutput())
- `CONTEXT`: Retrieved documents (from task outputs["context"])
- `INPUT`: Original query (from example.input())

**Why declare requirements?**
- Framework validates parameters exist before calling evaluator
- Clear documentation of what evaluator needs
- Prevents runtime errors from missing data

### EvalResult

Return evaluation result:

```java
EvalResult.of(
    name(),              // Evaluator name
    score,               // Score (0.0 to 1.0)
    threshold(),         // Pass threshold
    reason               // Human-readable explanation
);
```

**Score interpretation**:
- `>= threshold`: Pass
- `< threshold`: Fail

### Pattern Matching

```java
private static final Pattern CITATION_PATTERN = Pattern.compile("\\[\\d+\\]");

Matcher matcher = CITATION_PATTERN.matcher(response);
int citationCount = 0;
while (matcher.find()) {
    citationCount++;
}
```

**Regex breakdown**:
- `\\[`: Literal opening bracket `[`
- `\\d+`: One or more digits
- `\\]`: Literal closing bracket `]`

**Matches**: `[1]`, `[2]`, `[10]`, `[123]`

**Doesn't match**: `(1)`, `1.`, `[a]`, `[ 1 ]`

## Configuration

Register as a Spring bean:

```java
@Configuration
public class EvaluatorConfig {

    @Bean
    public CitationQualityEvaluator citationQualityEvaluator(
            @Value("${evaluation.citation.min:2}") int minCitations,
            @Value("${evaluation.citation.threshold:1.0}") double threshold) {
        return new CitationQualityEvaluator(minCitations, threshold);
    }
}
```

In `application.properties`:

```properties
# Require at least 2 citations
evaluation.citation.min=2

# Threshold 1.0 = must meet minimum (binary pass/fail)
evaluation.citation.threshold=1.0
```

## Usage Example

```java
// Add to evaluator list in DokimosEvaluationService
@Service
public class DokimosEvaluationService {

    private final CitationQualityEvaluator citationQualityEvaluator;

    private List<Evaluator> buildEvaluatorList(List<String> filter) {
        List<Evaluator> evaluators = new ArrayList<>();

        if (shouldInclude("faithfulness", filter)) {
            evaluators.add(faithfulnessEvaluator);
        }
        if (shouldInclude("citation-quality", filter)) {
            evaluators.add(citationQualityEvaluator);
        }

        return evaluators;
    }
}
```

**Evaluation output**:
```
citation-quality:
  - Score: 1.0
  - Reason: "Found 3 citations (minimum required: 2) - Meets threshold"
```

## Custom Evaluator Patterns

### Scoring Patterns

**Binary (pass/fail)**:
```java
double score = conditionMet ? 1.0 : 0.0;
```

**Proportional**:
```java
double score = (double) actualValue / targetValue;
score = Math.min(1.0, score);  // Cap at 1.0
```

**Inverse** (lower is better):
```java
double score = 1.0 - (errorRate / maxErrorRate);
score = Math.max(0.0, score);  // Floor at 0.0
```

### Rule-Based Evaluators

**Keyword presence**:
```java
public class KeywordEvaluator extends BaseEvaluator {

    private final List<String> requiredKeywords;

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String response = testCase.actualOutput().toLowerCase();

        long matchCount = requiredKeywords.stream()
            .filter(response::contains)
            .count();

        double score = (double) matchCount / requiredKeywords.size();

        return EvalResult.of(name(), score, threshold(),
            String.format("Found %d/%d keywords", matchCount, requiredKeywords.size())
        );
    }
}
```

**Sentiment analysis**:
```java
public class SentimentEvaluator extends BaseEvaluator {

    private final SentimentAnalyzer analyzer;
    private final String expectedSentiment;

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String response = testCase.actualOutput();
        String sentiment = analyzer.analyze(response);

        double score = sentiment.equals(expectedSentiment) ? 1.0 : 0.0;

        return EvalResult.of(name(), score, threshold(),
            String.format("Sentiment: %s (expected: %s)", sentiment, expectedSentiment)
        );
    }
}
```

**Length validation**:
```java
public class LengthRangeEvaluator extends BaseEvaluator {

    private final int minLength;
    private final int maxLength;

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String response = testCase.actualOutput();
        int length = response.length();

        boolean inRange = length >= minLength && length <= maxLength;
        double score = inRange ? 1.0 : 0.0;

        return EvalResult.of(name(), score, threshold(),
            String.format("Length: %d (range: %d-%d)", length, minLength, maxLength)
        );
    }
}
```

### LLM-Based Evaluators

Use an LLM to judge quality:

```java
public class ToneEvaluator extends BaseEvaluator {

    private final ChatModel judgeLLM;
    private final String expectedTone;

    @Override
    protected EvalResult runEvaluation(EvalTestCase testCase) {
        String response = testCase.actualOutput();

        String prompt = String.format("""
            Analyze the tone of this response and classify it as one of:
            professional, casual, formal, friendly, technical

            Response: %s

            Classification:
            """, response);

        String classification = judgeLLM.chat(prompt).trim().toLowerCase();

        double score = classification.contains(expectedTone) ? 1.0 : 0.0;

        return EvalResult.of(name(), score, threshold(),
            String.format("Tone: %s (expected: %s)", classification, expectedTone)
        );
    }
}
```

## Testing

```java
@Test
void shouldPassWhenCitationsPresent() {
    CitationQualityEvaluator evaluator = new CitationQualityEvaluator(2, 1.0);

    String response = "Java is a programming language [1]. It was created in 1995 [2].";

    EvalTestCase testCase = new EvalTestCase(
        response,  // actualOutput
        null,      // expectedOutput
        null,      // context
        null       // input
    );

    EvalResult result = evaluator.runEvaluation(testCase);

    assertThat(result.score()).isEqualTo(1.0);
    assertThat(result.passed()).isTrue();
    assertThat(result.reason()).contains("Found 2 citations");
}

@Test
void shouldFailWhenCitationsMissing() {
    CitationQualityEvaluator evaluator = new CitationQualityEvaluator(2, 1.0);

    String response = "Java is a programming language created in 1995.";

    EvalTestCase testCase = new EvalTestCase(response, null, null, null);

    EvalResult result = evaluator.runEvaluation(testCase);

    assertThat(result.score()).isEqualTo(0.0);
    assertThat(result.passed()).isFalse();
    assertThat(result.reason()).contains("Found 0 citations");
}
```

## Best Practices

**Make evaluators focused and testable**:
- One quality dimension per evaluator
- Clear, testable criteria
- Easy to validate in unit tests

**Provide meaningful reasons**:
- Explain why score was assigned
- Include specific counts or values
- Help debugging evaluation failures

**Choose appropriate thresholds**:
- Binary checks: threshold 1.0 (must pass)
- Proportional checks: threshold 0.7-0.9 (allow some slack)
- Experiment with thresholds on validation set

**Consider computational cost**:
- Rule-based evaluators are fast and cheap
- LLM-based evaluators are slow and expensive
- Use LLM evaluators for complex judgments only

**Version evaluators with code**:
- Track evaluator changes in git
- Document evaluation criteria
- Regression test evaluator behavior

## Key Takeaways

- **Custom evaluators enable domain-specific quality checks** beyond generic metrics
- **BaseEvaluator simplifies implementation** with common setup and validation
- **Pattern matching and rule-based logic** provide fast, deterministic evaluation
- **EvalResult captures score, threshold, and explanation** for transparency
- **Different scoring patterns fit different criteria**—binary, proportional, or inverse

## Next Steps

Learn how **ProductionRAGController** integrates all production features into a robust REST API.

---

**Next Chapter**: [11 - ProductionRAGController: Production-Ready API](./11-production-rag-controller.md)
