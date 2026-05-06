# Search Comparison Response

## Overview

Before building a RAG system, you often want to understand how different retrieval strategies perform on your data. Should you use vector search? Keyword search? A hybrid approach?

The `SearchComparisonResponse` DTO powers a comparison endpoint that runs all three methods in parallel and returns their results side-by-side. This is invaluable for evaluating retrieval quality and understanding when each approach shines.

## The SearchComparisonResponse Record

```java
package com.techcorp.assistant.rag;

import java.util.List;

public record SearchComparisonResponse(
        String query,
        List<String> vectorResults,
        List<String> keywordResults,
        List<String> hybridResults) {
}
```

### Structure

This record contains:

- **query**: The search query that was executed (useful for logging and debugging)
- **vectorResults**: Results from semantic vector search (cosine similarity on embeddings)
- **keywordResults**: Results from BM25 keyword search (exact term matching)
- **hybridResults**: Results from hybrid search (vector + keyword combined with RRF)

Each result list contains the **text content** of the retrieved segments, ordered by relevance (most relevant first).

### Why Three Result Sets?

Each retrieval method has different strengths:

**Vector Search (Semantic)**
- Finds documents by meaning, not exact words
- Great for: paraphrases, synonyms, conceptual similarity
- Weakness: misses exact term matches (e.g., product codes, acronyms)

**Keyword Search (Lexical)**
- Finds documents by exact term matching with TF-IDF weighting
- Great for: acronyms, product names, specific terminology
- Weakness: misses paraphrases and conceptual similarity

**Hybrid Search**
- Combines both using Reciprocal Rank Fusion
- Great for: getting the best of both worlds
- Complexity: more moving parts, harder to tune

## Using the Comparison Endpoint

### Request Example

```bash
curl -X POST http://localhost:8080/api/v1/rag/compare \
  -H "Content-Type: application/json" \
  -d '{
    "query": "VPN setup",
    "topK": 3
  }'
```

### Response Example

```json
{
  "query": "VPN setup",
  "vectorResults": [
    "To connect remotely, use the company VPN. Download the VPN client from IT portal...",
    "Remote access requires authentication via VPN. Install the client software...",
    "For remote work, establish a secure connection using the virtual private network..."
  ],
  "keywordResults": [
    "VPN setup instructions: 1. Download VPN client 2. Enter credentials 3. Connect...",
    "The VPN configuration requires admin approval. Submit a ticket to enable VPN access...",
    "VPN troubleshooting: If VPN connection fails, check firewall settings..."
  ],
  "hybridResults": [
    "VPN setup instructions: 1. Download VPN client 2. Enter credentials 3. Connect...",
    "To connect remotely, use the company VPN. Download the VPN client from IT portal...",
    "For remote work, establish a secure connection using the virtual private network..."
  ]
}
```

### What This Reveals

**Vector Results**: Notice the third result says "virtual private network" instead of "VPN" - semantic search found it anyway because the concepts are similar in embedding space.

**Keyword Results**: The top result contains "VPN" twice - BM25 keyword search ranks exact term matches highly. It found the most relevant document with precise terminology.

**Hybrid Results**: The top result is from keyword search (exact "VPN setup"), but the second result is from vector search (semantic match). Hybrid combines the strengths of both.

## Real-World Use Cases

### 1. Evaluating Your Retrieval Strategy

Run comparison on representative queries from your domain:

```bash
# Example: Technical support queries
curl -X POST .../compare -d '{"query": "reset password", "topK": 5}'
curl -X POST .../compare -d '{"query": "SEV1 incident", "topK": 5}'
curl -X POST .../compare -d '{"query": "how to deploy", "topK": 5}'
```

Analyze the results:
- If vector search consistently wins → use vector-only (simpler)
- If keyword search consistently wins → your docs may have important exact terms
- If results vary by query type → use hybrid

### 2. Understanding Domain Characteristics

**Example: Medical Records**
- "diabetes" (keyword) vs "high blood sugar" (vector) → both should retrieve similar docs
- If they don't, your embedding model may not be domain-tuned

**Example: Legal Documents**
- "breach of contract" (exact legal term) → keyword should excel
- "agreement violation" (layperson phrasing) → vector should excel
- Hybrid should handle both

### 3. Debugging Retrieval Issues

When users report "bad results":
1. Run the comparison endpoint with their query
2. Check which method(s) actually found relevant results
3. This tells you whether the problem is:
   - Vocabulary mismatch (use multi-query or HyDE)
   - Missing exact terms (tune keyword search)
   - Low-quality embeddings (try a different embedding model)

## Pattern: Structured Concurrency for Parallel Search

In the controller, notice how we run all three searches in parallel:

```java
@PostMapping("/compare")
public ResponseEntity<SearchComparisonResponse> compareSearchMethods(
        @Valid @RequestBody CompareRequest request) {
    try (var scope = StructuredTaskScope.open(...)) {
        var vectorTask = scope.fork(() -> hybridSearchService.vectorOnlySearch(...));
        var keywordTask = scope.fork(() -> hybridSearchService.keywordOnlySearch(...));
        var hybridTask = scope.fork(() -> hybridSearchService.hybridSearch(...));

        scope.join();

        return ResponseEntity.ok(new SearchComparisonResponse(
            request.query(),
            vectorTask.get(),
            keywordTask.get(),
            hybridTask.get()));
    }
}
```

**Why structured concurrency?**

Old way (raw threads):
- Manually manage thread lifecycle
- Easy to leak threads if exceptions occur
- No parent-child relationship between tasks

Structured concurrency (Java 21+):
- Tasks are scoped to the try block
- If any task fails, all are cancelled automatically
- Tasks complete before the scope exits
- Cleaner, safer parallel execution

**Performance gain:**
- Sequential: 300ms + 200ms + 400ms = 900ms
- Parallel: max(300ms, 200ms, 400ms) = 400ms
- 2.25x speedup with three methods

## Design Patterns

### 1. Returning Text vs. Metadata

This DTO returns `List<String>` (just the text content). An alternative design:

```java
public record SearchComparisonResponse(
    String query,
    List<SearchResult> vectorResults,
    List<SearchResult> keywordResults,
    List<SearchResult> hybridResults
) {
    public record SearchResult(String text, double score, Map<String, Object> metadata) {}
}
```

**Trade-off:**
- More info (scores, metadata) → better for analysis
- More complex → harder to read for simple use cases

Start simple (our current design), add complexity when needed.

### 2. Why Echo the Query?

Including `query` in the response seems redundant - the client already knows it. But it's useful for:

- **Logging**: When debugging, you see both query and results in one object
- **Async systems**: If requests are queued, the response needs context
- **Batch processing**: Multiple queries processed together need identification

### 3. Empty Result Lists

What if a method finds nothing?

```json
{
  "query": "quantum flux capacitor",
  "vectorResults": [],
  "keywordResults": [],
  "hybridResults": []
}
```

This is valid and informative - it tells you *none* of the methods found anything, suggesting the query is outside your knowledge base.

## Example Analysis Session

Let's compare retrieval methods on a real query:

```bash
curl -X POST .../compare -d '{"query": "SEV1 incident response", "topK": 3}'
```

**Result:**

```json
{
  "query": "SEV1 incident response",
  "vectorResults": [
    "During critical outages, follow the emergency response protocol...",
    "High-priority incidents require immediate escalation to on-call engineers...",
    "Incident management procedures include notification and resolution steps..."
  ],
  "keywordResults": [
    "SEV1 incidents must be reported within 15 minutes. Follow the SEV1 playbook...",
    "Critical SEV1 response: 1. Page on-call 2. Create incident channel 3. Start bridge...",
    "Severity 1 (SEV1) issues require immediate response. Contact the incident commander..."
  ],
  "hybridResults": [
    "SEV1 incidents must be reported within 15 minutes. Follow the SEV1 playbook...",
    "Critical SEV1 response: 1. Page on-call 2. Create incident channel 3. Start bridge...",
    "During critical outages, follow the emergency response protocol..."
  ]
}
```

**Analysis:**

- **Vector search** found conceptually similar documents but missed the exact "SEV1" terminology
- **Keyword search** correctly prioritized documents with "SEV1" (domain-specific acronym)
- **Hybrid search** returned keyword results first (exact match) but included the semantic result third

**Conclusion:** For this query, keyword search wins. But hybrid is the safest choice because it handles both exact and semantic queries well.

## Key Takeaways

1. **Comparison is a debugging tool**: Use it to understand your retrieval behavior
2. **Parallel execution matters**: Structured concurrency makes it safe and fast
3. **Different strategies for different queries**: No one-size-fits-all retrieval method
4. **Start simple, iterate**: Return just text content, add metadata later if needed
5. **Echo the query**: Include it in the response for logging and debugging

## What's Next?

Now that we understand the API surface, let's dive into the first major component of advanced RAG: query transformation.

---

**Next Chapter**: [04 - Query Transformer](./04-query-transformer.md)
