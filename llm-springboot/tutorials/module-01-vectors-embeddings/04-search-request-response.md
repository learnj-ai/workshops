# Chapter: SearchRequest and SearchResponse - The API Contract

## Introduction: The Restaurant Menu and Receipt

Imagine you're at a restaurant. You don't walk into the kitchen and start grabbing ingredients—instead, you use a **menu** to communicate what you want, and you receive a **receipt** that itemizes exactly what you got. This standardized communication makes the restaurant run smoothly.

In our vector search system, **SearchRequest** is your menu order and **SearchResponse** is your itemized receipt. They form the **API contract**—a formal agreement between the client (who wants to search) and the server (who performs the search) about exactly what information will be exchanged.

Why do we need this structure?
- **Validation**: Ensures clients provide all required information
- **Defaults**: Sensible fallbacks if optional fields aren't specified
- **Documentation**: Self-documenting code that clearly shows what's needed
- **Type Safety**: The compiler catches mistakes before runtime

## How It Works: Request and Response DTOs

### SearchRequest: What You're Asking For

```java
package com.techcorp.assistant.search;

import com.techcorp.assistant.chunking.ChunkingStrategy;
import com.techcorp.assistant.similarity.SearchMetric;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SearchRequest(
        @NotBlank String query,
        @Min(1) @Max(20) int maxResults,
        SearchMetric metric,
        ChunkingStrategy chunkingStrategy) {

    public SearchRequest {
        maxResults = (maxResults == 0) ? 5 : maxResults;
        metric = (metric == null) ? SearchMetric.COSINE : metric;
        chunkingStrategy = (chunkingStrategy == null) ? ChunkingStrategy.RECURSIVE : chunkingStrategy;
    }
}
```

**Key Features:**

1. **@NotBlank query**: The search text (required). The annotation ensures it's not null or empty.

2. **@Min(1) @Max(20) maxResults**: Limits results to 1-20. This prevents clients from requesting thousands of results and overwhelming the system.

3. **SearchMetric metric**: Which similarity algorithm to use (defaults to COSINE if null).

4. **ChunkingStrategy chunkingStrategy**: Which chunking strategy to use (defaults to RECURSIVE if null).

5. **Compact constructor**: Java record feature that applies defaults automatically. If a field is null or zero, it gets replaced with a sensible default.

### SearchResponse: What You Get Back

```java
package com.techcorp.assistant.search;

import com.techcorp.assistant.chunking.ChunkingStrategy;
import com.techcorp.assistant.similarity.SearchMetric;
import java.util.List;

public record SearchResponse(
        int embeddingDimension,
        SearchMetric metric,
        ChunkingStrategy chunkingStrategy,
        int indexedSegmentCount,
        List<SearchResult> results) {
}
```

**Key Features:**

1. **embeddingDimension**: Tells you the vector size used (e.g., 384 for AllMiniLmL6V2). Useful for debugging.

2. **metric**: Echoes back which similarity metric was used.

3. **chunkingStrategy**: Echoes back which chunking strategy was used.

4. **indexedSegmentCount**: How many total chunks are in the index for this strategy. Helps you understand corpus size.

5. **results**: The actual search results (List<SearchResult>).

## Code Deep Dive

### Request Validation

The validation annotations on `SearchRequest` trigger Spring Boot's validation framework:

```java
@PostMapping("/vector")
public ResponseEntity<SearchResponse> vectorSearch(@Valid @RequestBody SearchRequest request) {
    // @Valid triggers validation - if it fails, GlobalExceptionHandler catches it
    // ...
}
```

If validation fails (e.g., query is blank), the `GlobalExceptionHandler` intercepts it and returns a clear error message:

```json
{
  "error": "Validation failed",
  "details": [
    {
      "field": "query",
      "message": "must not be blank"
    }
  ]
}
```

### Default Values

The compact constructor applies defaults intelligently:

```java
// Client sends minimal request
POST /api/v1/search/vector
{
  "query": "vacation policy"
}

// The compact constructor fills in:
// maxResults → 5
// metric → COSINE
// chunkingStrategy → RECURSIVE
```

This makes the API friendly: clients can provide as little or as much configuration as they want.

### Typical Usage

```java
// Create a request
SearchRequest request = new SearchRequest(
    "How do I submit a reimbursement?",
    10,
    SearchMetric.COSINE,
    ChunkingStrategy.PARAGRAPH
);

// Call the service
SearchResponse response = vectorStoreService.search(request);

// Access the results
System.out.println("Found " + response.results().size() + " results");
System.out.println("Using " + response.metric() + " metric");
System.out.println("Index has " + response.indexedSegmentCount() + " total segments");

for (SearchResult result : response.results()) {
    System.out.println("Score: " + result.score());
    System.out.println("Content: " + result.content());
}
```

## Relationships: API Contracts Connect Layers

### Who Uses SearchRequest
- **VectorSearchController**: Receives and validates `SearchRequest` from HTTP clients
- **VectorStoreService**: Accepts `SearchRequest` and extracts query, maxResults, metric, and strategy

### Who Creates SearchResponse
- **VectorStoreService**: Constructs `SearchResponse` with metadata and search results
- **VectorSearchController**: Returns `SearchResponse` as JSON to HTTP clients

### The Full Request/Response Flow

```
HTTP Client
    ↓
POST /api/v1/search/vector
{
  "query": "vacation days",
  "maxResults": 5
}
    ↓
[VectorSearchController validates SearchRequest]
    ↓
[VectorStoreService.search(request)]
    ↓
[Creates SearchResponse with results]
    ↓
{
  "embeddingDimension": 384,
  "metric": "COSINE",
  "chunkingStrategy": "RECURSIVE",
  "indexedSegmentCount": 47,
  "results": [...]
}
    ↓
HTTP Client receives JSON
```

## Key Takeaways

- **SearchRequest and SearchResponse define the API contract** between clients and the search service
- **Validation annotations** ensure data integrity and provide automatic error messages
- **Default values** make the API user-friendly by allowing minimal requests
- **Java records** provide immutability and clarity for DTOs
- **These DTOs decouple the API layer from internal implementations** (e.g., clients don't know about IndexedSegment or SearchMatch)

## Next Steps

Now that you understand the API contract, let's dive into the **configuration layer**. In the next chapter, **EmbeddingConfiguration**, you'll learn how the embedding model is set up and why choosing the right model matters for your search quality.
