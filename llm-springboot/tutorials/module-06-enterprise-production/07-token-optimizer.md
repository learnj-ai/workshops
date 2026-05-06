# Chapter: TokenOptimizer - Cost Optimization Through Token Management

## Introduction

**TokenOptimizer** reduces LLM API costs by intelligently managing token usage. It selects the most relevant context segments within a token budget and compresses prompts by removing redundant content, helping you control costs while maintaining response quality.

## Code

```java
@Service
public class TokenOptimizer {

    private static final Logger log = LoggerFactory.getLogger(TokenOptimizer.class);
    private static final double WORDS_PER_TOKEN = 0.75;

    @Value("${token-optimizer.max-tokens:4000}")
    private int maxTokens;

    @Value("${token-optimizer.compression-enabled:true}")
    private boolean compressionEnabled;

    public List<TokenizedSegment> optimizeContext(List<String> segments, List<Double> relevanceScores) {
        if (segments.size() != relevanceScores.size()) {
            throw new IllegalArgumentException("Segments and scores must have same size");
        }

        // Create tokenized segments
        List<TokenizedSegment> tokenized = segments.stream()
                .map(this::tokenize)
                .collect(Collectors.toList());

        // Add relevance scores
        for (int i = 0; i < tokenized.size(); i++) {
            TokenizedSegment segment = tokenized.get(i);
            tokenized.set(i, new TokenizedSegment(
                    segment.content(),
                    segment.tokenCount(),
                    relevanceScores.get(i)
            ));
        }

        // Select segments within budget
        List<TokenizedSegment> selected = selectByBudget(tokenized, maxTokens);

        log.info("Optimized context: {} segments selected, {} tokens used of {} budget",
                selected.size(), totalTokens(selected), maxTokens);

        return selected;
    }

    public String compressPrompt(String prompt) {
        if (!compressionEnabled) {
            return prompt;
        }

        // Remove redundant whitespace
        String compressed = prompt.replaceAll("\\s+", " ").trim();

        // Remove filler words
        compressed = compressed.replaceAll("\\b(basically|actually|literally|just|really|very)\\b", "");

        // Normalize multiple spaces again after filler removal
        compressed = compressed.replaceAll("\\s+", " ").trim();

        int originalTokens = estimateTokens(prompt);
        int compressedTokens = estimateTokens(compressed);

        log.debug("Compressed prompt: {} -> {} tokens ({} reduction)",
                originalTokens, compressedTokens, originalTokens - compressedTokens);

        return compressed;
    }

    private TokenizedSegment tokenize(String content) {
        int tokens = estimateTokens(content);
        return new TokenizedSegment(content, tokens, 0.0);
    }

    private int estimateTokens(String text) {
        int words = text.split("\\s+").length;
        return (int) Math.ceil(words / WORDS_PER_TOKEN);
    }

    private List<TokenizedSegment> selectByBudget(List<TokenizedSegment> segments, int budget) {
        // Sort by relevance score descending
        List<TokenizedSegment> sorted = segments.stream()
                .sorted(Comparator.comparingDouble(TokenizedSegment::relevanceScore).reversed())
                .collect(Collectors.toList());

        // Select segments until budget is exhausted
        List<TokenizedSegment> selected = new ArrayList<>();
        int usedTokens = 0;

        for (TokenizedSegment segment : sorted) {
            if (usedTokens + segment.tokenCount() <= budget) {
                selected.add(segment);
                usedTokens += segment.tokenCount();
            }
        }

        return selected;
    }

    private int totalTokens(List<TokenizedSegment> segments) {
        return segments.stream()
                .mapToInt(TokenizedSegment::tokenCount)
                .sum();
    }

    public record TokenizedSegment(
            String content,
            int tokenCount,
            double relevanceScore
    ) {}
}
```

## Key Concepts

### Token Estimation

LLM APIs charge by tokens, not characters:

```java
private static final double WORDS_PER_TOKEN = 0.75;

private int estimateTokens(String text) {
    int words = text.split("\\s+").length;
    return (int) Math.ceil(words / WORDS_PER_TOKEN);
}
```

**Why 0.75 words per token?**
- GPT tokenizers split on whitespace and punctuation
- Average English word ~1.3 tokens
- Simple approximation: 1 token ≈ 0.75 words (or 4 characters)

**For exact counts**, use tokenizer libraries:
```java
// With tiktoken-java
Tokenizer tokenizer = Tiktoken.getEncoding("cl100k_base");
int tokens = tokenizer.encode(text).size();
```

### Context Selection by Relevance

```java
public List<TokenizedSegment> optimizeContext(List<String> segments, List<Double> relevanceScores) {
    // 1. Tokenize all segments
    // 2. Attach relevance scores from vector similarity
    // 3. Sort by relevance (highest first)
    // 4. Select greedily until budget exhausted
}
```

**Benefits**:
- Maximizes relevance within token budget
- Predictable costs (never exceeds budget)
- Improves response quality (only relevant context)

**Trade-offs**:
- May exclude moderately relevant context
- Simple greedy selection (not optimal)
- Doesn't consider segment ordering

### Prompt Compression

```java
public String compressPrompt(String prompt) {
    // Remove redundant whitespace
    String compressed = prompt.replaceAll("\\s+", " ").trim();

    // Remove filler words
    compressed = compressed.replaceAll("\\b(basically|actually|literally|just|really|very)\\b", "");

    return compressed;
}
```

**Simple compression techniques**:
- Whitespace normalization (10-20% reduction)
- Filler word removal (5-10% reduction)
- Abbreviations and acronyms
- Remove punctuation (careful—may affect meaning)

**Advanced compression**:
- Extractive summarization (GPT-3.5 for GPT-4 context)
- Sentence compression models
- Domain-specific abbreviations

## Usage Example

```java
@Service
public class OptimizedRAGService {

    private final TokenOptimizer optimizer;

    public RAGResponse query(String query) {
        // Retrieve top 10 segments
        List<SearchMatch> matches = vectorStore.search(query, 10);

        List<String> segments = matches.stream()
            .map(SearchMatch::content)
            .collect(Collectors.toList());

        List<Double> scores = matches.stream()
            .map(SearchMatch::score)
            .collect(Collectors.toList());

        // Optimize: select best segments within token budget
        List<TokenizedSegment> optimized = optimizer.optimizeContext(segments, scores);

        String context = optimized.stream()
            .map(TokenizedSegment::content)
            .collect(Collectors.joining("\n\n"));

        // Compress prompt
        String prompt = buildPrompt(query, context);
        prompt = optimizer.compressPrompt(prompt);

        // Generate response
        return llm.generate(prompt);
    }
}
```

## Configuration

```properties
# Maximum tokens for context
token-optimizer.max-tokens=4000

# Enable/disable compression
token-optimizer.compression-enabled=true
```

**Sizing the budget**:
- **GPT-3.5**: Context window 4K-16K tokens, set budget to 2K-4K
- **GPT-4**: Context window 8K-128K tokens, set budget to 4K-8K
- Reserve tokens for: system prompt, query, expected response

Example breakdown:
```
Total budget: 8000 tokens
- System prompt: 200 tokens
- User query: 100 tokens
- Expected response: 500 tokens
- Context budget: 7200 tokens
```

## Cost Impact

**Without optimization**:
```
10 segments × 500 tokens = 5000 tokens
Cost: 5000 tokens × $0.03 / 1000 = $0.15 per request
```

**With optimization** (4000 token budget):
```
8 segments × 500 tokens = 4000 tokens
Cost: 4000 tokens × $0.03 / 1000 = $0.12 per request
20% cost reduction
```

**With compression** (10% reduction):
```
4000 tokens × 0.9 = 3600 tokens
Cost: 3600 tokens × $0.03 / 1000 = $0.108 per request
28% total cost reduction
```

**At scale** (1M requests/month):
- Without optimization: $150,000/month
- With optimization: $108,000/month
- **Savings: $42,000/month**

## Advanced Optimizations

### Dynamic Budget Allocation

Adjust budget based on query complexity:

```java
public int calculateBudget(String query, int maxBudget) {
    // Complex queries get more context
    int queryComplexity = query.split("\\s+").length;

    if (queryComplexity > 20) {
        return maxBudget;  // Full budget
    } else if (queryComplexity > 10) {
        return (int) (maxBudget * 0.7);  // 70% budget
    } else {
        return (int) (maxBudget * 0.5);  // 50% budget
    }
}
```

### Segment Deduplication

Remove redundant segments:

```java
public List<String> deduplicate(List<String> segments) {
    Set<String> seen = new HashSet<>();
    List<String> unique = new ArrayList<>();

    for (String segment : segments) {
        // Normalize and hash
        String normalized = segment.toLowerCase().replaceAll("\\s+", " ");
        if (!seen.contains(normalized)) {
            seen.add(normalized);
            unique.add(segment);
        }
    }

    return unique;
}
```

### Tiered Context Selection

Include mix of highly relevant and diverse context:

```java
public List<TokenizedSegment> selectTiered(List<TokenizedSegment> segments, int budget) {
    // Allocate 70% budget to top segments
    int tier1Budget = (int) (budget * 0.7);
    List<TokenizedSegment> tier1 = selectByBudget(segments, tier1Budget);

    // Allocate 30% budget to diverse segments
    int tier2Budget = budget - totalTokens(tier1);
    List<TokenizedSegment> remaining = segments.stream()
        .filter(s -> !tier1.contains(s))
        .collect(Collectors.toList());

    List<TokenizedSegment> tier2 = selectDiverse(remaining, tier2Budget);

    List<TokenizedSegment> selected = new ArrayList<>();
    selected.addAll(tier1);
    selected.addAll(tier2);

    return selected;
}
```

## Monitoring

Track token usage:

```java
@Service
public class TokenOptimizer {

    private final Counter tokensOptimized;
    private final Gauge budgetUtilization;

    public TokenOptimizer(MeterRegistry registry) {
        this.tokensOptimized = Counter.builder("tokens.optimized")
            .register(registry);

        this.budgetUtilization = Gauge.builder("tokens.budget.utilization", this::calculateUtilization)
            .register(registry);
    }

    public List<TokenizedSegment> optimizeContext(List<String> segments, List<Double> relevanceScores) {
        List<TokenizedSegment> selected = selectByBudget(tokenized, maxTokens);

        int saved = totalTokens(tokenized) - totalTokens(selected);
        tokensOptimized.increment(saved);

        return selected;
    }
}
```

**Key metrics**:
- `tokens.optimized`: Total tokens saved
- `tokens.budget.utilization`: Percentage of budget used
- `tokens.per.request`: Average tokens per request
- `cost.per.request`: Estimated cost per request

## Best Practices

**Start with conservative budgets**:
- Begin with small budget
- Gradually increase if quality suffers
- Monitor response quality metrics

**Validate compression doesn't degrade quality**:
- A/B test compressed vs uncompressed
- Monitor accuracy and user satisfaction
- Disable compression if quality drops

**Use exact tokenizers in production**:
- Simple word-based estimation has ~10% error
- Use tiktoken or official tokenizer libraries
- Cache tokenization results

**Consider response tokens in budget**:
- Budget includes input + output tokens
- Reserve tokens for expected response length
- Truncate responses if they exceed budget

**Log optimization decisions**:
- Record segments selected/excluded
- Track relevance scores and token counts
- Analyze patterns to improve selection

## Testing

```java
@Test
void shouldSelectSegmentsWithinBudget() {
    TokenOptimizer optimizer = new TokenOptimizer();
    ReflectionTestUtils.setField(optimizer, "maxTokens", 100);

    List<String> segments = List.of(
        "Segment 1 with 20 tokens",  // ~27 tokens
        "Segment 2 with 20 tokens",  // ~27 tokens
        "Segment 3 with 20 tokens",  // ~27 tokens
        "Segment 4 with 20 tokens"   // ~27 tokens
    );

    List<Double> scores = List.of(0.95, 0.90, 0.85, 0.80);

    List<TokenizedSegment> selected = optimizer.optimizeContext(segments, scores);

    int totalTokens = selected.stream()
        .mapToInt(TokenizedSegment::tokenCount)
        .sum();

    assertThat(totalTokens).isLessThanOrEqualTo(100);
    assertThat(selected).hasSizeLessThan(segments.size());

    // Verify highest scores selected
    assertThat(selected.get(0).relevanceScore()).isEqualTo(0.95);
}

@Test
void shouldCompressPrompt() {
    TokenOptimizer optimizer = new TokenOptimizer();
    ReflectionTestUtils.setField(optimizer, "compressionEnabled", true);

    String prompt = "This is basically  a   really   very simple test.";
    String compressed = optimizer.compressPrompt(prompt);

    assertThat(compressed).isEqualTo("This is a simple test.");
}
```

## Key Takeaways

- **Token optimization reduces LLM costs** by selecting only the most relevant context
- **Greedy selection by relevance** maximizes information density within budget
- **Prompt compression removes redundancy** saving 10-30% tokens with minimal quality impact
- **Token budgets provide cost predictability** preventing unexpected API bills
- **Monitoring optimization metrics** enables continuous improvement of selection strategies

## Next Steps

Learn how **DokimosEvaluationService** systematically evaluates RAG quality using the Dokimos framework.

---

**Next Chapter**: [08 - DokimosEvaluationService: Systematic RAG Evaluation](./08-dokimos-evaluation-service.md)
