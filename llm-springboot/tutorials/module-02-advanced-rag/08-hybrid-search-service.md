# Hybrid Search Service: Combining Vector and Keyword Search

## Overview

We've built two complementary retrieval systems:
- **Vector search**: Finds semantically similar documents
- **Keyword search**: Finds exact term matches

Each has strengths and weaknesses. `HybridSearchService` combines them using **Reciprocal Rank Fusion (RRF)** to get the best of both worlds, then applies re-ranking for a final quality boost.

This is the heart of advanced retrieval - a multi-stage pipeline that maximizes both recall and precision.

## The HybridSearchService

```java
package com.techcorp.assistant.rag;

import com.techcorp.assistant.store.VectorStoreService;
import dev.langchain4j.data.segment.TextSegment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    private static final int RRF_RANK_CONSTANT = 60;

    private final VectorStoreService vectorStore;
    private final KeywordSearchService keywordSearch;
    private final ReRanker reRanker;

    public HybridSearchService(
            VectorStoreService vectorStore,
            KeywordSearchService keywordSearch,
            ReRanker reRanker) {
        this.vectorStore = vectorStore;
        this.keywordSearch = keywordSearch;
        this.reRanker = reRanker;
    }

    public List<TextSegment> hybridSearch(String query, int topK) {
        int retrievalSize = topK * 2;

        // Stage 1: Parallel retrieval from both sources
        List<TextSegment> vectorResults = vectorStore.searchSegments(query, retrievalSize);
        List<TextSegment> keywordResults = keywordSearch.search(query, retrievalSize);

        log.debug("Vector search returned {} results, keyword search returned {} results",
                vectorResults.size(), keywordResults.size());

        // Stage 2: Merge results using Reciprocal Rank Fusion
        List<TextSegment> mergedResults = reciprocalRankFusion(
                vectorResults, keywordResults, retrievalSize);

        log.debug("RRF merged to {} results", mergedResults.size());

        // Stage 3: Re-rank with the re-ranker
        return reRanker.rerank(query, mergedResults, topK);
    }

    public List<TextSegment> vectorOnlySearch(String query, int topK) {
        return vectorStore.searchSegments(query, topK);
    }

    public List<TextSegment> keywordOnlySearch(String query, int topK) {
        return keywordSearch.search(query, topK);
    }

    List<TextSegment> reciprocalRankFusion(
            List<TextSegment> list1,
            List<TextSegment> list2,
            int maxResults) {

        Map<String, Double> scores = new HashMap<>();
        Map<String, TextSegment> segmentsByText = new HashMap<>();

        // Score from list 1
        for (int i = 0; i < list1.size(); i++) {
            TextSegment segment = list1.get(i);
            String text = segment.text();
            scores.merge(text, 1.0 / (RRF_RANK_CONSTANT + i + 1), Double::sum);
            segmentsByText.putIfAbsent(text, segment);
        }

        // Score from list 2
        for (int i = 0; i < list2.size(); i++) {
            TextSegment segment = list2.get(i);
            String text = segment.text();
            scores.merge(text, 1.0 / (RRF_RANK_CONSTANT + i + 1), Double::sum);
            segmentsByText.putIfAbsent(text, segment);
        }

        // Sort by combined RRF score
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> segmentsByText.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }
}
```

## Three-Stage Hybrid Search Pipeline

### Stage 1: Parallel Retrieval

```java
int retrievalSize = topK * 2;  // Retrieve more than we need

List<TextSegment> vectorResults = vectorStore.searchSegments(query, retrievalSize);
List<TextSegment> keywordResults = keywordSearch.search(query, retrievalSize);
```

**Why retrieve more?**

If we want 5 final results (`topK = 5`), we retrieve 10 from each source because:
1. There will be overlap (same documents in both lists)
2. After merging and re-ranking, we want enough diversity
3. Gives re-ranker more candidates to choose from

**Why parallel?**

These are independent operations - no need to wait for one to finish before starting the other. In practice, you might use:
- Java's parallel streams (simple)
- CompletableFuture (async)
- Structured concurrency (Java 21+, used in the controller)

Sequential: 100ms (vector) + 50ms (keyword) = 150ms
Parallel: max(100ms, 50ms) = 100ms

### Stage 2: Reciprocal Rank Fusion (RRF)

```java
List<TextSegment> mergedResults = reciprocalRankFusion(
        vectorResults, keywordResults, retrievalSize);
```

**RRF** is an elegant algorithm for merging ranked lists from different sources without needing to normalize scores.

#### The RRF Algorithm

For each document in each ranked list, compute:

```
RRF_score = 1 / (k + rank)
```

Where:
- `k` is a constant (typically 60)
- `rank` is the position in the list (0-indexed)

Then sum the scores across all lists where the document appears.

#### Example Walkthrough

**Input:**

Vector search results:
1. Doc A (rank 0)
2. Doc B (rank 1)
3. Doc C (rank 2)

Keyword search results:
1. Doc B (rank 0)
2. Doc D (rank 1)
3. Doc A (rank 2)

**RRF Calculations:**

Doc A:
- Vector score: 1 / (60 + 0) = 1/60 = 0.0167
- Keyword score: 1 / (60 + 2) = 1/62 = 0.0161
- **Total: 0.0328**

Doc B:
- Vector score: 1 / (60 + 1) = 1/61 = 0.0164
- Keyword score: 1 / (60 + 0) = 1/60 = 0.0167
- **Total: 0.0331** (highest!)

Doc C:
- Vector score: 1 / (60 + 2) = 1/62 = 0.0161
- Keyword score: 0 (not in keyword results)
- **Total: 0.0161**

Doc D:
- Vector score: 0 (not in vector results)
- Keyword score: 1 / (60 + 1) = 1/61 = 0.0164
- **Total: 0.0164**

**Final ranking:**
1. Doc B (0.0331) - ranked highly by both!
2. Doc A (0.0328) - ranked highly by both
3. Doc D (0.0164) - only in keyword
4. Doc C (0.0161) - only in vector

Documents that appear in both lists get a bonus (sum of two scores).

#### Why RRF Works

**Rank-based, not score-based:**

Vector search scores (cosine similarity): 0.0 to 1.0
Keyword search scores (BM25): 0.0 to 100+

These aren't comparable! You can't just average them.

RRF uses **positions**, which are always comparable. First place is first place, regardless of the underlying score.

**Favors consensus:**

Documents that appear high in multiple lists get boosted. This filters out:
- Vector search false positives (semantically similar but not actually relevant)
- Keyword search false positives (matches words but wrong context)

**Diminishing returns:**

The difference between rank 1 and rank 2 is significant:
- Rank 1: 1/61 = 0.0164
- Rank 2: 1/62 = 0.0161

But the difference between rank 50 and rank 51 is tiny:
- Rank 50: 1/110 = 0.0091
- Rank 51: 1/111 = 0.0090

This naturally down-weights low-ranked results.

#### The k Constant

```java
private static final int RRF_RANK_CONSTANT = 60;
```

**What does k do?**

It controls how much top results are favored:

- **Lower k (e.g., 10)**: Top results get much higher scores, steep curve
- **Higher k (e.g., 100)**: Scores are more evenly distributed, flatter curve

**Example with k=10:**
- Rank 0: 1/10 = 0.1
- Rank 5: 1/15 = 0.067
- Difference: 0.033 (significant)

**Example with k=100:**
- Rank 0: 1/100 = 0.01
- Rank 5: 1/105 = 0.0095
- Difference: 0.0005 (small)

**Standard value: k=60**

This is empirically proven to work well across many domains. It balances:
- Rewarding top results
- Not completely ignoring lower-ranked results

You can tune k for your domain, but 60 is a safe default.

### Stage 3: Re-Ranking

```java
return reRanker.rerank(query, mergedResults, topK);
```

After RRF merges the results, we have a diverse candidate pool. The re-ranker applies a final quality pass to ensure the top K are truly the most relevant.

This is where the EmbeddingBasedReRanker (or a cross-encoder in production) refines the ordering.

## Implementation Details

### Deduplication in RRF

```java
Map<String, Double> scores = new HashMap<>();
Map<String, TextSegment> segmentsByText = new HashMap<>();
```

**Why key by text content?**

The same document might appear in both vector and keyword results. We want to:
- Combine their scores (sum)
- Avoid returning duplicates

Using `text()` as the key automatically handles this. If a document appears in both lists, `scores.merge()` sums the RRF scores.

**Potential issue: Near-duplicates**

If two segments have slightly different text (extra whitespace, punctuation), they'll be treated as different documents.

Production systems might:
- Normalize text before using as key
- Use document IDs instead of text
- Apply fuzzy deduplication (Levenshtein distance)

### Ordering Guarantees

```java
return scores.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .limit(maxResults)
        .map(entry -> segmentsByText.get(entry.getKey()))
        .filter(Objects::nonNull)
        .toList();
```

**Sorted by RRF score:** Highest scores first (`.reversed()`)

**Limited to maxResults:** Typically `topK * 2` to give re-ranker enough candidates

**Filter null safety:** Defensive programming in case of Map inconsistencies

## When Hybrid Search Excels

### Query Type 1: Mixed Intent

**Query:** "SEV1 incident response time"

**Vector search finds:**
- "Critical issues require fast resolution"
- "Emergency response procedures"

**Keyword search finds:**
- "SEV1 incidents must be resolved within 15 minutes"
- "Incident response time SLA: SEV1 = 15min, SEV2 = 1hr"

**Hybrid (RRF) combines:**
- Best of both: exact "SEV1" match + semantic "incident response"
- Top result: "SEV1 incidents must be resolved within 15 minutes"

### Query Type 2: Acronyms with Context

**Query:** "VPN setup for remote work"

**Vector search finds:**
- "Working remotely requires secure connection"
- "Remote access authentication"

**Keyword search finds:**
- "VPN setup instructions"
- "VPN configuration guide"

**Hybrid:**
- Boosts documents with "VPN" (keyword) that are also about remote work (vector)
- Filters out "VPN" docs about troubleshooting (not relevant to "setup")

### Query Type 3: Synonyms and Exact Terms

**Query:** "reset my password"

**Vector search finds:**
- "Change your credentials"
- "Recover account access"

**Keyword search finds:**
- "Password reset process"
- "Reset password link"

**Hybrid:**
- Finds documents with "password" and "reset" (keyword match)
- Also finds semantically similar "credentials" and "account recovery" (vector match)
- Ranks exact matches higher (RRF consensus)

## Comparison Methods for Evaluation

The service provides methods to compare strategies:

```java
public List<TextSegment> vectorOnlySearch(String query, int topK) {
    return vectorStore.searchSegments(query, topK);
}

public List<TextSegment> keywordOnlySearch(String query, int topK) {
    return keywordSearch.search(query, topK);
}
```

**Use in the comparison endpoint:**

```java
@PostMapping("/compare")
public ResponseEntity<SearchComparisonResponse> compareSearchMethods(
        @Valid @RequestBody CompareRequest request) {
    List<String> vectorResults = hybridSearchService.vectorOnlySearch(request.query(), request.topK())
            .stream().map(TextSegment::text).toList();

    List<String> keywordResults = hybridSearchService.keywordOnlySearch(request.query(), request.topK())
            .stream().map(TextSegment::text).toList();

    List<String> hybridResults = hybridSearchService.hybridSearch(request.query(), request.topK())
            .stream().map(TextSegment::text).toList();

    return ResponseEntity.ok(new SearchComparisonResponse(
            request.query(), vectorResults, keywordResults, hybridResults));
}
```

This lets you empirically evaluate which method works best for your data and queries.

## Performance Considerations

### Parallelization Opportunities

Currently, vector and keyword searches run sequentially. For production, parallelize:

```java
CompletableFuture<List<TextSegment>> vectorFuture =
    CompletableFuture.supplyAsync(() -> vectorStore.searchSegments(query, retrievalSize));

CompletableFuture<List<TextSegment>> keywordFuture =
    CompletableFuture.supplyAsync(() -> keywordSearch.search(query, retrievalSize));

List<TextSegment> vectorResults = vectorFuture.join();
List<TextSegment> keywordResults = keywordFuture.join();
```

Or use structured concurrency (Java 21+) as shown in the controller.

### Caching

For frequently searched queries, cache results:

```java
@Cacheable("hybrid-search")
public List<TextSegment> hybridSearch(String query, int topK) {
    // Implementation...
}
```

Trade-off: Stale results vs. reduced latency.

### Tuning retrievalSize

```java
int retrievalSize = topK * 2;
```

**Lower multiplier (1.5x):**
- Faster (less retrieval, less re-ranking)
- Less diversity in final results

**Higher multiplier (3x):**
- Slower
- More diversity, potentially better quality

**Sweet spot: 2x**

Empirically tested to balance speed and quality.

## Testing Hybrid Search

```java
@Test
void testHybridSearchCombinesVectorAndKeyword() {
    // Setup: Mock vector search returns [A, B, C]
    //        Mock keyword search returns [B, D, E]
    when(vectorStore.searchSegments(anyString(), anyInt()))
        .thenReturn(List.of(segmentA, segmentB, segmentC));
    when(keywordSearch.search(anyString(), anyInt()))
        .thenReturn(List.of(segmentB, segmentD, segmentE));

    HybridSearchService service = new HybridSearchService(vectorStore, keywordSearch, reRanker);

    List<TextSegment> results = service.hybridSearch("test query", 5);

    // Should contain segments from both sources
    assertTrue(results.contains(segmentA) || results.contains(segmentD));
}

@Test
void testRRFBoostsDocumentsInBothLists() {
    List<TextSegment> list1 = List.of(
        segment("common doc"),
        segment("only in list1")
    );
    List<TextSegment> list2 = List.of(
        segment("common doc"),
        segment("only in list2")
    );

    HybridSearchService service = new HybridSearchService(vectorStore, keywordSearch, reRanker);
    List<TextSegment> merged = service.reciprocalRankFusion(list1, list2, 10);

    // "common doc" appears in both, should be ranked first
    assertEquals("common doc", merged.get(0).text());
}
```

## Key Takeaways

1. **Hybrid search combines strengths** of vector and keyword retrieval
2. **RRF merges ranked lists** without score normalization
3. **Three-stage pipeline**: retrieve, merge, re-rank
4. **Consensus ranking**: Documents in both lists get boosted
5. **Tunable k constant**: Controls how much top results are favored
6. **Comparison methods**: Evaluate which strategy works best

## What's Next?

Now we have powerful retrieval. Let's orchestrate the complete RAG pipeline: query transformation, hybrid search, and grounded generation.

---

**Next Chapter**: [09 - RAG Service: Complete Pipeline](./09-rag-service.md)
