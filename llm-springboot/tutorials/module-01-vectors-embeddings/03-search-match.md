# Chapter: SearchMatch - Your Search Result Package

## Introduction: The Prize Box

Imagine you're at a carnival where you toss rings onto bottles to win prizes. When you succeed, the attendant doesn't just hand you a random toy—they give you a prize box containing:
- **The prize itself** (what you won)
- **A scorecard** (how well you threw)
- **A tag** (information about the prize: size, color, origin)

That's exactly what a `SearchMatch` is in our vector search system. When you search for information, each result comes packaged with three essential pieces:
1. The **content** (the actual text that matches your search)
2. The **score** (how relevant it is to your query)
3. The **metadata** (extra information about where it came from)

`SearchMatch` exists because finding information isn't just about *what* you found—it's also about *how well it matches* and *what context it has*. This simple but powerful structure makes search results both useful and understandable.

## How It Works: The Three-Part Result

A `SearchMatch` is a Java record that acts as an immutable container for search results. It has three main responsibilities:

1. **Hold the matched content**: The actual text from the document that matched your search query
2. **Carry the relevance score**: A numerical value (typically between 0 and 1) indicating how similar the content is to your search query
3. **Preserve metadata**: Additional information like the source document, page number, timestamps, or any other contextual data

When the `VectorStoreService` performs a search, it doesn't return raw `IndexedSegment` objects (which are internal storage structures). Instead, it transforms them into `SearchMatch` objects—a clean, user-friendly format that contains exactly what the caller needs to know about each result.

## Code Deep Dive

### The SearchMatch Record

```java
// SearchMatch.java
package com.techcorp.assistant.store;

import dev.langchain4j.data.document.Metadata;

/**
 * Represents a single search result from the vector store.
 *
 * @param content  The text content that matched the search query
 * @param score    Similarity score (0.0 to 1.0, higher is better)
 * @param metadata Additional information about this content (source, page, etc.)
 */
public record SearchMatch(String content, double score, Metadata metadata) {
}
```

This is one of the simplest classes in our entire system, yet it's fundamental to how search works. Let's break down each component:

- **`String content`**: The actual text that was found. This comes from the `IndexedSegment.segment().text()` and represents a chunk of the original document.

- **`double score`**: A number representing how similar this content is to what you searched for. A score of 1.0 means perfect similarity; 0.0 means no similarity. This is calculated by the `SimilarityCalculator`.

- **`Metadata metadata`**: A flexible container for any extra information. Common metadata includes:
  - `source`: Which document this came from
  - `page`: What page number (for PDFs)
  - `chunk_index`: Which chunk this was in the original document
  - Any custom fields you want to track

**Why a record?** Java records are perfect for this use case. They're immutable (can't be changed after creation), automatically generate equals/hashCode/toString methods, and clearly communicate that this is a data container, not a complex object with behavior.

### Creating SearchMatch Objects

The `VectorStoreService` creates `SearchMatch` objects during search operations using this transformation method:

```java
/**
 * Converts an IndexedSegment (internal storage format) into a SearchMatch
 * (external result format) by calculating the similarity score.
 */
private SearchMatch toSearchMatch(
        IndexedSegment indexedSegment,
        Embedding queryEmbedding,
        SearchMetric metric) {

    // Calculate how similar this stored segment is to the search query
    double score = similarityCalculator.score(
            queryEmbedding.vector(),              // The search query (as a vector)
            indexedSegment.embedding().vector(),  // The stored document chunk (as a vector)
            metric);                              // How to measure similarity (cosine, dot product, etc.)

    // Package up the three components into a SearchMatch
    return new SearchMatch(
            indexedSegment.segment().text(),      // The actual text content
            score,                                // The calculated similarity score
            indexedSegment.segment().metadata()); // The original metadata
}
```

This method is the bridge between the internal storage world (`IndexedSegment`) and the external search results world (`SearchMatch`). Notice how it:
1. Extracts the text content from the nested `segment().text()`
2. Computes a fresh similarity score between the query and stored embedding
3. Passes through the metadata unchanged

### SearchMatch in Action

Here's how `SearchMatch` objects flow through a typical search:

```java
// 1. User makes a search request
SearchRequest request = new SearchRequest("How do I reset my password?", 5);

// 2. VectorStoreService.search() converts the query to an embedding,
//    finds similar segments, and creates SearchMatch objects
List<SearchMatch> matches = vectorStoreService.search(request);

// 3. Each SearchMatch contains everything needed to show results
for (SearchMatch match : matches) {
    System.out.println("Score: " + match.score());
    System.out.println("Content: " + match.content());
    System.out.println("Source: " + match.metadata().getString("source"));
    System.out.println("---");
}

// Output might look like:
// Score: 0.89
// Content: To reset your password, click on the "Forgot Password" link...
// Source: user-guide.pdf
// ---
// Score: 0.82
// Content: Password resets can be requested from the login page...
// Source: faq.pdf
// ---
```

## Relationships: SearchMatch in the Ecosystem

`SearchMatch` sits at a critical junction in the search pipeline, connecting several components:

### Input Side: Where SearchMatch Comes From

- **IndexedSegment**: The internal storage format that `SearchMatch` is created from. An `IndexedSegment` contains the embedding vector, while `SearchMatch` contains the computed similarity score.

- **SimilarityCalculator**: Computes the `score` field by comparing the query embedding to the stored embedding using the specified `SearchMetric`.

- **VectorStoreService**: The factory for `SearchMatch` objects. It performs the search, calculates scores, and transforms `IndexedSegment` objects into `SearchMatch` objects.

### Output Side: What Uses SearchMatch

- **SearchResponse**: Contains a list of `SearchMatch` objects. This is what the `VectorStoreService.search()` method returns.

- **VectorSearchController**: Receives `SearchMatch` objects from the service layer and transforms them into `SearchResult` objects (a simpler DTO for the REST API).

### The Transformation Chain

```
User Query
    ↓
[EmbeddingService] → Query Embedding
    ↓
[VectorStoreService] → Search for similar IndexedSegments
    ↓
[SimilarityCalculator] → Calculate scores
    ↓
[VectorStoreService] → Transform to SearchMatch objects ← YOU ARE HERE
    ↓
[SearchResponse] → Package results
    ↓
[VectorSearchController] → Transform to SearchResult DTOs
    ↓
JSON Response to User
```

## Key Takeaways

- **SearchMatch is a simple, immutable container** for search results, consisting of content (text), score (relevance), and metadata (context).

- **It bridges internal and external representations**: `IndexedSegment` is for storage; `SearchMatch` is for results. The transformation happens during the search operation.

- **The score is computed fresh for each search** by comparing the query embedding to the stored embedding using the `SimilarityCalculator` and specified `SearchMetric`.

- **Metadata provides context**: While the content and score tell you *what* matched and *how well*, metadata tells you *where it came from* and other useful contextual information.

- **Records are ideal for this use case**: Java records provide immutability, automatic equality checking, and clear intent as a data container.

## Next Steps

Now that you understand how individual search results are packaged, you're ready to explore the API contracts that define how clients interact with the search system. In the next chapter, **SearchRequest/SearchResponse**, you'll learn how users submit search queries and receive structured results through a well-defined REST API interface.
