# Embedding-Based ReRanker

## Overview

The `EmbeddingBasedReRanker` is a practical implementation of the `ReRanker` interface that uses cosine similarity between query and candidate embeddings to re-score documents.

It's a **bi-encoder approach** - query and documents are encoded independently, then compared. This is less accurate than cross-encoders but much faster and doesn't require additional model downloads.

For learning purposes and lightweight systems, this is a great starting point. In production, you might upgrade to a cross-encoder for better quality.

## The Implementation

```java
package com.techcorp.assistant.rag;

import com.techcorp.assistant.embeddings.EmbeddingService;
import com.techcorp.assistant.similarity.SimilarityCalculator;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EmbeddingBasedReRanker implements ReRanker {

    private final EmbeddingService embeddingService;
    private final SimilarityCalculator similarityCalculator;

    public EmbeddingBasedReRanker(EmbeddingService embeddingService, SimilarityCalculator similarityCalculator) {
        this.embeddingService = embeddingService;
        this.similarityCalculator = similarityCalculator;
    }

    @Override
    public List<TextSegment> rerank(String query, List<TextSegment> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }

        Embedding queryEmbedding = embeddingService.generateEmbedding(query);

        record ScoredCandidate(TextSegment segment, double score) {}

        return candidates.stream()
                .map(candidate -> {
                    Embedding candidateEmbedding = embeddingService.generateEmbedding(candidate.text());
                    double score = similarityCalculator.cosineSimilarity(
                            queryEmbedding.vector(), candidateEmbedding.vector());
                    return new ScoredCandidate(candidate, score);
                })
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
                .limit(topK)
                .map(ScoredCandidate::segment)
                .toList();
    }
}
```

## How It Works

### Step-by-Step Execution

**Input:**
```java
String query = "password reset process";
List<TextSegment> candidates = [
    "password reset instructions for users...",
    "account recovery and authentication...",
    "security best practices guide...",
    "user profile management..."
];
int topK = 2;
```

**Step 1: Embed the query**

```java
Embedding queryEmbedding = embeddingService.generateEmbedding(query);
// Result: [0.12, -0.05, 0.33, ...] (384 dimensions)
```

**Step 2: Embed each candidate and calculate similarity**

```java
for (TextSegment candidate : candidates) {
    Embedding candidateEmbedding = embeddingService.generateEmbedding(candidate.text());
    double score = similarityCalculator.cosineSimilarity(queryEmbedding.vector(), candidateEmbedding.vector());
}
```

**Results:**
- "password reset instructions..." → similarity: 0.89
- "account recovery and authentication..." → similarity: 0.75
- "security best practices guide..." → similarity: 0.62
- "user profile management..." → similarity: 0.48

**Step 3: Sort by score (highest first)**

```
1. "password reset instructions..." (0.89)
2. "account recovery and authentication..." (0.75)
3. "security best practices guide..." (0.62)
4. "user profile management..." (0.48)
```

**Step 4: Limit to topK**

```java
.limit(topK)  // Take top 2
```

**Output:**
```java
[
    "password reset instructions...",
    "account recovery and authentication..."
]
```

## Why This Works

### Semantic Similarity

Cosine similarity measures how "aligned" two vectors are in high-dimensional space.

**Example:**

Query: "password reset"
- Embedding: Strong signals for concepts like authentication, security, credentials

Candidate 1: "password reset instructions"
- Embedding: Very similar concepts (authentication, security, credentials)
- Cosine similarity: 0.89 (very similar)

Candidate 2: "user profile management"
- Embedding: Different concepts (profiles, settings, customization)
- Cosine similarity: 0.48 (somewhat different)

The re-ranker finds documents that are semantically closest to the query.

### Difference from Initial Vector Search

**Wait, isn't this the same as vector search?**

Almost, but with a subtle difference:

**Initial vector search:**
- Compares query embedding to **pre-computed** document embeddings
- Fast (embeddings computed once at startup)
- Broad strokes similarity

**Re-ranking with fresh embeddings:**
- Compares query embedding to **freshly computed** candidate embeddings
- Slower (embeddings computed for each re-rank)
- Can use different embedding strategies (e.g., longer context)

In this implementation, they're functionally similar. But in production, you might:
- Use a different embedding model for re-ranking (specialized for re-ranking)
- Include more context in the candidate embeddings during re-ranking
- Use query-time prompt engineering to improve embeddings

## Limitations of Bi-Encoder Re-Ranking

### 1. No Query-Document Interaction

Bi-encoders encode query and document **independently**, so they can't capture interactions.

**Example:**

Query: "best laptop for programming"
Document: "This laptop is best for programming"

**Bi-encoder:**
- Query → embedding (computed separately)
- Document → embedding (computed separately)
- Similarity → dot product

**Cross-encoder:**
- Input: "[CLS] best laptop for programming [SEP] This laptop is best for programming [SEP]"
- Model sees "best" in query aligns with "best" in document
- More accurate relevance

### 2. Limited Nuance

Bi-encoders struggle with subtle distinctions:

**Query:** "python web framework"

Candidate A: "Flask is a Python web framework"
Candidate B: "Django is a Python web framework"

Both have nearly identical embeddings (same concepts: Python, web, framework). Bi-encoder can't distinguish them well.

Cross-encoder could:
- Look for "micro" vs "full-featured"
- Consider tone and context
- Make finer-grained distinctions

### 3. Context Limitations

Bi-encoders are limited by embedding model input length (typically 512 tokens).

Long documents might be truncated, losing important context. Cross-encoders can handle this better with sliding window approaches.

## When to Use Embedding-Based Re-Ranking

### Good For:

1. **Learning and experimentation**
   - No additional model downloads
   - Easy to understand
   - Fast to implement

2. **Lightweight systems**
   - Small candidate pools (< 50)
   - Low-latency requirements
   - Limited compute resources

3. **General-purpose retrieval**
   - Broad semantic matching
   - Doesn't need fine-grained distinctions

### Upgrade to Cross-Encoder When:

1. **Quality matters most**
   - Medical, legal, financial domains
   - User-facing production search

2. **Fine-grained distinctions needed**
   - Choosing between very similar documents
   - Ranking subtly different answers

3. **You have compute budget**
   - Can afford 50-100ms re-ranking time
   - Have GPU available for faster inference

## Pattern: Record for Scoring

```java
record ScoredCandidate(TextSegment segment, double score) {}
```

This is a common pattern in ranking systems:

**Why not a class?**
- Records are immutable (can't accidentally change scores)
- Concise (one line vs. ~20 lines for a class)
- Built-in `equals()`, `hashCode()`, `toString()` for free

**Why not just a tuple?**
- Named fields (`segment`, `score`) are clearer than `_1`, `_2`
- Type-safe (compiler catches mistakes)

**Usage:**

```java
return candidates.stream()
        .map(candidate -> {
            double score = computeScore(candidate);
            return new ScoredCandidate(candidate, score);
        })
        .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed())
        .limit(topK)
        .map(ScoredCandidate::segment)  // Extract just the segment
        .toList();
```

Clean separation: score for sorting, then discard scores in final output.

## Performance Characteristics

### Time Complexity

For `N` candidates and embedding dimension `D`:

1. **Generate query embedding**: O(D) - fixed cost
2. **Generate candidate embeddings**: O(N × D)
3. **Calculate similarities**: O(N × D) - dot product for each pair
4. **Sort**: O(N log N)
5. **Limit to topK**: O(K)

**Total**: O(N × D + N log N)

For typical values (N=30, D=384):
- Embedding generation: ~30ms per document → ~900ms total
- Similarity calculation: negligible (< 1ms)
- Sorting: negligible (< 1ms)

**Total time**: ~900ms (dominated by embedding generation)

### Optimization: Batch Embedding

Current implementation embeds one candidate at a time:

```java
candidates.stream()
    .map(candidate -> embeddingService.generateEmbedding(candidate.text()))
```

Better: Batch embed all candidates:

```java
List<String> texts = candidates.stream().map(TextSegment::text).toList();
List<Embedding> embeddings = embeddingService.generateEmbeddingsBatch(texts);
```

**Why faster?**
- Model processes multiple texts in parallel
- Less overhead (one model call vs. N model calls)
- GPU utilization improved

**Speed improvement:**
- Sequential: 30 × 30ms = 900ms
- Batch: ~200ms (4.5x faster)

## Comparison to Other Re-Rankers

| Approach | Speed | Accuracy | Complexity |
|----------|-------|----------|------------|
| **Embedding-based (bi-encoder)** | Fast (~1s for 30 docs) | Good | Low |
| **Cross-encoder** | Medium (~5s for 30 docs) | Excellent | Medium |
| **LLM-based** | Slow (~30s for 30 docs) | Best | Low (if you have LLM) |
| **No re-ranking** | Fastest | Baseline | None |

## Testing the Re-Ranker

```java
@Test
void testReRankerOrdersByRelevance() {
    EmbeddingService embeddingService = new AllMiniLmL6V2EmbeddingModel();
    SimilarityCalculator calculator = new SimilarityCalculator();
    EmbeddingBasedReRanker reRanker = new EmbeddingBasedReRanker(embeddingService, calculator);

    List<TextSegment> candidates = List.of(
        TextSegment.from("password reset instructions for users"),
        TextSegment.from("unrelated document about coffee"),
        TextSegment.from("account recovery and password help")
    );

    List<TextSegment> reranked = reRanker.rerank("password reset", candidates, 2);

    assertEquals(2, reranked.size());
    assertTrue(reranked.get(0).text().contains("password"));
    assertTrue(reranked.get(1).text().contains("password"));
    // "coffee" document should be filtered out
}

@Test
void testReRankerHandlesEmptyCandidates() {
    EmbeddingBasedReRanker reRanker = createReRanker();

    List<TextSegment> reranked = reRanker.rerank("test", List.of(), 5);

    assertTrue(reranked.isEmpty());
}

@Test
void testReRankerRespectsTopK() {
    EmbeddingBasedReRanker reRanker = createReRanker();

    List<TextSegment> candidates = IntStream.range(0, 10)
        .mapToObj(i -> TextSegment.from("document " + i))
        .toList();

    List<TextSegment> reranked = reRanker.rerank("query", candidates, 3);

    assertEquals(3, reranked.size());
}
```

## Real-World Usage Example

In `HybridSearchService`:

```java
public List<TextSegment> hybridSearch(String query, int topK) {
    int retrievalSize = topK * 2;  // Retrieve more than we need

    // Stage 1: Retrieve candidates
    List<TextSegment> vectorResults = vectorStore.searchSegments(query, retrievalSize);
    List<TextSegment> keywordResults = keywordSearch.search(query, retrievalSize);

    // Stage 2: Merge with RRF
    List<TextSegment> mergedResults = reciprocalRankFusion(vectorResults, keywordResults, retrievalSize);

    // Stage 3: Re-rank to improve final results
    return reRanker.rerank(query, mergedResults, topK);
}
```

**Flow:**
1. Retrieve 10 candidates from vector search
2. Retrieve 10 candidates from keyword search
3. Merge to ~15-20 unique candidates (some overlap)
4. Re-rank those 15-20 to get the best 5

The re-ranker acts as a quality filter on the merged results.

## Key Takeaways

1. **Bi-encoder re-ranking** uses cosine similarity of embeddings
2. **Simpler than cross-encoders** but less accurate
3. **Good for learning** and lightweight systems
4. **Batch embedding** can significantly improve performance
5. **Records for scoring** keep code clean and type-safe
6. **Upgrade path exists** to cross-encoders when needed

## What's Next?

Now we have all the pieces for retrieval. Let's combine vector search, keyword search, and re-ranking into hybrid search.

---

**Next Chapter**: [08 - Hybrid Search Service](./08-hybrid-search-service.md)
