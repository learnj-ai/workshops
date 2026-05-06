# Keyword Search Service: BM25 Implementation

## Overview

Vector search is powerful, but it has blind spots. It can miss exact term matches - imagine searching for "SEV1 incident" and getting documents about "high priority issues" but not the one that says "SEV1" explicitly.

`KeywordSearchService` implements **BM25**, a term-frequency based algorithm that ranks documents by how well they match the exact words in the query. It's the lexical counterpart to semantic vector search.

When combined with vector search in a hybrid approach, you get the best of both worlds: semantic understanding AND exact term matching.

## The KeywordSearchService

```java
package com.techcorp.assistant.rag;

import com.techcorp.assistant.chunking.ChunkingStrategy;
import com.techcorp.assistant.store.IndexedSegment;
import com.techcorp.assistant.store.VectorStoreService;
import dev.langchain4j.data.segment.TextSegment;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KeywordSearchService {

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;

    private final Supplier<List<IndexedSegment>> segmentSupplier;

    @Autowired
    public KeywordSearchService(VectorStoreService vectorStoreService) {
        this.segmentSupplier = () -> vectorStoreService.getAllSegments(ChunkingStrategy.RECURSIVE);
    }

    KeywordSearchService(List<IndexedSegment> fixedSegments) {
        this.segmentSupplier = () -> fixedSegments;
    }

    public List<TextSegment> search(String query, int maxResults) {
        List<IndexedSegment> allSegments = segmentSupplier.get();
        if (allSegments.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        double avgDocLength = allSegments.stream()
                .mapToInt(seg -> tokenize(seg.segment().text()).size())
                .average()
                .orElse(1.0);

        int totalDocs = allSegments.size();

        // Precompute document frequencies for each query term
        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (String term : queryTerms) {
            int df = (int) allSegments.stream()
                    .filter(seg -> tokenize(seg.segment().text()).contains(term))
                    .count();
            documentFrequencies.put(term, df);
        }

        record ScoredSegment(TextSegment segment, double score) {}

        return allSegments.stream()
                .map(indexed -> {
                    double score = computeBM25Score(
                            indexed.segment().text(), queryTerms,
                            documentFrequencies, totalDocs, avgDocLength);
                    return new ScoredSegment(indexed.segment(), score);
                })
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredSegment::score).reversed())
                .limit(maxResults)
                .map(ScoredSegment::segment)
                .toList();
    }

    private double computeBM25Score(
            String documentText,
            Set<String> queryTerms,
            Map<String, Integer> documentFrequencies,
            int totalDocs,
            double avgDocLength) {

        Set<String> docTerms = tokenize(documentText);
        int docLength = docTerms.size();
        Map<String, Long> termFrequencies = countTermFrequencies(documentText);

        double score = 0.0;
        for (String term : queryTerms) {
            int df = documentFrequencies.getOrDefault(term, 0);
            if (df == 0) {
                continue;
            }

            // IDF component: log((N - df + 0.5) / (df + 0.5) + 1)
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

            // TF component with BM25 saturation
            long tf = termFrequencies.getOrDefault(term, 0L);
            double tfNormalized = (tf * (BM25_K1 + 1.0))
                    / (tf + BM25_K1 * (1.0 - BM25_B + BM25_B * docLength / avgDocLength));

            score += idf * tfNormalized;
        }

        return score;
    }

    private Map<String, Long> countTermFrequencies(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(t -> !t.isEmpty() && t.length() > 1)
                .collect(Collectors.toSet());
    }
}
```

## What is BM25?

**BM25** (Best Matching 25) is a ranking function that scores documents based on term frequency (TF) and inverse document frequency (IDF).

### The Intuition

Think of BM25 as answering two questions:

1. **How often does the query term appear in this document?** (Term Frequency)
   - A document with "password" 5 times is likely more relevant to "password reset" than one with it once

2. **How rare is this term across all documents?** (Inverse Document Frequency)
   - The term "the" appears everywhere, so it's not informative
   - The term "SEV1" appears rarely, so finding it is highly relevant

BM25 combines these with saturation (diminishing returns) and document length normalization (long documents aren't automatically ranked higher).

### The Formula

```
BM25(D, Q) = Σ IDF(qi) × (f(qi, D) × (k1 + 1)) / (f(qi, D) + k1 × (1 - b + b × |D| / avgdl))
```

Where:
- `D` = document
- `Q` = query
- `qi` = term i in the query
- `f(qi, D)` = frequency of term qi in document D
- `|D|` = length of document D
- `avgdl` = average document length in the collection
- `k1` = term frequency saturation parameter (typically 1.2)
- `b` = length normalization parameter (typically 0.75)

**Don't panic** - we'll break this down step by step.

## Implementation Walkthrough

### Step 1: Tokenization

```java
private Set<String> tokenize(String text) {
    return Arrays.stream(text.toLowerCase().split("\\W+"))
            .filter(t -> !t.isEmpty() && t.length() > 1)
            .collect(Collectors.toSet());
}
```

**What it does:**
- Convert to lowercase ("Password" → "password")
- Split on non-word characters (`\W+` matches spaces, punctuation)
- Filter out empty strings and single characters (noise)
- Return a Set (deduplicated)

**Example:**
```
Input: "How to reset PASSWORD? Contact IT."
Tokens: {how, to, reset, password, contact, it}
```

### Step 2: Precompute Statistics

```java
// Average document length
double avgDocLength = allSegments.stream()
        .mapToInt(seg -> tokenize(seg.segment().text()).size())
        .average()
        .orElse(1.0);

// Document frequencies (how many docs contain each query term)
Map<String, Integer> documentFrequencies = new HashMap<>();
for (String term : queryTerms) {
    int df = (int) allSegments.stream()
            .filter(seg -> tokenize(seg.segment().text()).contains(term))
            .count();
    documentFrequencies.put(term, df);
}
```

**Why precompute?**

For 100 documents and 3 query terms, computing these on-the-fly would mean:
- Tokenizing each document 3 times → 300 tokenizations
- Precomputing → 100 tokenizations

Much faster to compute once and reuse.

### Step 3: Score Each Document

```java
return allSegments.stream()
        .map(indexed -> {
            double score = computeBM25Score(
                    indexed.segment().text(), queryTerms,
                    documentFrequencies, totalDocs, avgDocLength);
            return new ScoredSegment(indexed.segment(), score);
        })
        .filter(scored -> scored.score() > 0)  // Remove documents with no matching terms
        .sorted(Comparator.comparingDouble(ScoredSegment::score).reversed())
        .limit(maxResults)
        .map(ScoredSegment::segment)
        .toList();
```

**Flow:**
1. For each document, compute BM25 score
2. Wrap segment + score in a record
3. Filter out zero-score documents (no matching terms)
4. Sort by score descending (highest first)
5. Limit to top K
6. Extract just the segments (discard scores)

### Step 4: BM25 Score Calculation

Let's break down `computeBM25Score`:

```java
private double computeBM25Score(
        String documentText,
        Set<String> queryTerms,
        Map<String, Integer> documentFrequencies,
        int totalDocs,
        double avgDocLength) {

    Set<String> docTerms = tokenize(documentText);
    int docLength = docTerms.size();
    Map<String, Long> termFrequencies = countTermFrequencies(documentText);

    double score = 0.0;
    for (String term : queryTerms) {
        int df = documentFrequencies.getOrDefault(term, 0);
        if (df == 0) {
            continue;  // Term doesn't appear in any document
        }

        // IDF: Inverse Document Frequency
        double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

        // TF: Term Frequency with saturation
        long tf = termFrequencies.getOrDefault(term, 0L);
        double tfNormalized = (tf * (BM25_K1 + 1.0))
                / (tf + BM25_K1 * (1.0 - BM25_B + BM25_B * docLength / avgDocLength));

        score += idf * tfNormalized;
    }

    return score;
}
```

#### IDF Calculation

```java
double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);
```

**Intuition:** Rare terms are more valuable.

**Example:**

If we have 100 documents:
- Term "the" appears in 99 documents:
  - `IDF = log((100 - 99 + 0.5) / (99 + 0.5) + 1) = log(1.015) ≈ 0.015`
  - Very low score (not informative)

- Term "SEV1" appears in 2 documents:
  - `IDF = log((100 - 2 + 0.5) / (2 + 0.5) + 1) = log(40.4) ≈ 3.70`
  - High score (very informative)

#### TF Normalization with Saturation

```java
long tf = termFrequencies.getOrDefault(term, 0L);
double tfNormalized = (tf * (BM25_K1 + 1.0))
        / (tf + BM25_K1 * (1.0 - BM25_B + BM25_B * docLength / avgDocLength));
```

**Saturation:** Diminishing returns for repeated terms.

If a term appears 1 time: high contribution
If it appears 5 times: higher contribution, but not 5x
If it appears 100 times: only slightly higher than 5 times (saturates)

**Length normalization:** Longer documents naturally have more term occurrences. We adjust for this so short documents aren't unfairly penalized.

**Example:**

```
Query: "password reset"
Document A (50 words): "password" appears 2 times
Document B (200 words): "password" appears 5 times

Without normalization: B scores higher (5 > 2)
With normalization: Adjusted for length, they might score similarly
```

### Step 5: Combine TF and IDF

```java
score += idf * tfNormalized;
```

For each query term, multiply IDF (how rare) by TF (how frequent in this doc), and sum across all query terms.

**Example:**

```
Query: "SEV1 incident response"
Document: "SEV1 incidents require immediate response. Follow the SEV1 playbook..."

Term "sev1":
  - df = 2, IDF ≈ 3.70
  - tf = 2, tfNormalized ≈ 1.8
  - contribution = 3.70 × 1.8 ≈ 6.66

Term "incident":
  - df = 15, IDF ≈ 1.95
  - tf = 1, tfNormalized ≈ 1.0
  - contribution = 1.95 × 1.0 ≈ 1.95

Term "response":
  - df = 20, IDF ≈ 1.61
  - tf = 1, tfNormalized ≈ 1.0
  - contribution = 1.61 × 1.0 ≈ 1.61

Total BM25 score = 6.66 + 1.95 + 1.61 ≈ 10.22
```

Notice "SEV1" contributes the most because it's rare (high IDF) and appears twice (high TF).

## When Keyword Search Excels

### 1. Exact Terminology

**Query:** "API-3042 error"

Vector search might match:
- "Application error occurred"
- "Error code found in API logs"

Keyword search will prioritize:
- "API-3042 error: Connection timeout"

Because it matches the exact term "API-3042".

### 2. Acronyms and Abbreviations

**Query:** "VPN setup"

Vector search might match:
- "Virtual private network configuration"
- "Secure remote connection guide"

Keyword search will prioritize:
- "VPN setup instructions"

Because "VPN" is an exact match (and likely has high IDF).

### 3. Product Names and Codes

**Query:** "MacBook Pro M3"

Vector search might match:
- "Apple laptop specifications"
- "High-performance portable computer"

Keyword search will prioritize:
- "MacBook Pro M3 has 18 hours battery life"

Because all three terms match exactly.

## When Keyword Search Struggles

### 1. Vocabulary Mismatch

**Query:** "remote work"

Keyword search only finds documents with "remote" and "work".

It misses:
- "Telecommuting policy"
- "Work from home guidelines"
- "Off-site employee handbook"

Vector search would find these because they're semantically similar.

### 2. Synonyms and Paraphrases

**Query:** "car"

Keyword search finds "car" only.

It misses:
- "automobile"
- "vehicle"
- "sedan"

### 3. Conceptual Queries

**Query:** "how to improve productivity"

Keyword search looks for "improve" and "productivity".

It misses documents about:
- "Time management strategies" (concept of productivity, different words)
- "Getting more done" (same concept, no shared terms)

## Tuning BM25 Parameters

### k1: Term Frequency Saturation

```java
private static final double BM25_K1 = 1.2;
```

**Effect:**
- Lower k1 (e.g., 0.5): Faster saturation, repeated terms contribute less
- Higher k1 (e.g., 2.0): Slower saturation, repeated terms contribute more

**When to tune:**
- **Lower k1** if documents artificially repeat keywords for SEO (keyword stuffing)
- **Higher k1** if legitimate documents genuinely use important terms frequently

**Default:** 1.2 is standard and works well for most cases.

### b: Length Normalization

```java
private static final double BM25_B = 0.75;
```

**Effect:**
- b = 0: No length normalization (long docs favored)
- b = 1: Full length normalization (short and long docs treated equally)

**When to tune:**
- **b closer to 1** if your documents vary widely in length
- **b closer to 0** if all documents are similar length

**Default:** 0.75 is standard.

## Testing Keyword Search

```java
@Test
void testBM25RanksExactMatchesHigher() {
    List<IndexedSegment> segments = List.of(
        segment("SEV1 incidents require immediate response"),
        segment("High priority issues need quick action"),
        segment("Critical problems demand fast resolution")
    );

    KeywordSearchService service = new KeywordSearchService(segments);

    List<TextSegment> results = service.search("SEV1 incident", 3);

    assertEquals(1, results.size());  // Only one contains "SEV1"
    assertTrue(results.get(0).text().contains("SEV1 incidents"));
}

@Test
void testTermFrequencyAffectsRanking() {
    List<IndexedSegment> segments = List.of(
        segment("password reset"),
        segment("password reset password password"),
        segment("unrelated document")
    );

    KeywordSearchService service = new KeywordSearchService(segments);

    List<TextSegment> results = service.search("password", 2);

    assertEquals(2, results.size());
    // Document with more "password" occurrences should rank higher
    assertTrue(results.get(0).text().contains("password password"));
}

@Test
void testIDFBoostsRareTerms() {
    // Create segments where "SEV1" is rare but "issue" is common
    List<IndexedSegment> segments = new ArrayList<>();
    segments.add(segment("SEV1 incident requires response"));
    for (int i = 0; i < 50; i++) {
        segments.add(segment("issue " + i + " logged in system"));
    }

    KeywordSearchService service = new KeywordSearchService(segments);

    List<TextSegment> results = service.search("SEV1 issue", 1);

    // Document with rare "SEV1" should rank higher than docs with common "issue"
    assertTrue(results.get(0).text().contains("SEV1"));
}
```

## Key Takeaways

1. **BM25 is lexical**: It matches exact terms, not semantic meaning
2. **IDF favors rare terms**: "SEV1" contributes more than "the"
3. **TF saturates**: 5 occurrences aren't 5x better than 1
4. **Length normalization**: Long documents aren't automatically better
5. **Complements vector search**: Use both in hybrid search for best results
6. **Tunable parameters**: Adjust k1 and b for your domain if needed

## What's Next?

We have keyword search and vector search. But initial retrieval isn't perfect - that's where re-ranking comes in.

---

**Next Chapter**: [06 - ReRanker Interface](./06-reranker-interface.md)
