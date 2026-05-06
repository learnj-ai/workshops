# Query Transformer: Multi-Query and HyDE

## Overview

A single user query often misses relevant documents due to vocabulary mismatch. The user asks "How do I connect remotely?" but your docs say "VPN setup instructions". Vector search helps, but isn't perfect.

`QueryTransformer` solves this with two techniques:

1. **Multi-Query**: Generates alternative phrasings to capture different aspects of the information need
2. **HyDE** (Hypothetical Document Embeddings): Generates a hypothetical answer document that embeds better than the short query

These techniques increase **recall** (finding more relevant documents) at the cost of a few extra LLM calls.

## The QueryTransformer Service

```java
package com.techcorp.assistant.rag;

import dev.langchain4j.model.chat.ChatModel;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class QueryTransformer {

    private static final Logger log = LoggerFactory.getLogger(QueryTransformer.class);
    private static final int ALTERNATIVE_QUERY_COUNT = 3;

    private final ChatModel llm;

    public QueryTransformer(ChatModel llm) {
        this.llm = llm;
    }

    public List<String> multiQuery(String originalQuery) {
        try {
            String response = llm.chat(buildMultiQueryPrompt(originalQuery));
            List<String> alternatives = parseAlternativeQueries(response, originalQuery);
            log.debug("Multi-query generated {} alternatives for: {}", alternatives.size(), originalQuery);
            return alternatives;
        } catch (RuntimeException e) {
            log.warn("Multi-query generation failed for query: {}", originalQuery, e);
            return List.of();
        }
    }

    public String generateHypotheticalDocument(String query) {
        try {
            String hypothetical = llm.chat(buildHydePrompt(query)).trim();
            if (hypothetical.isBlank()) {
                log.debug("HyDE returned blank output for query: {}", query);
                return query;
            }
            log.debug("HyDE generated hypothetical document for: {}", query);
            return hypothetical;
        } catch (RuntimeException e) {
            log.warn("HyDE generation failed for query: {}", query, e);
            return query;
        }
    }

    private String buildMultiQueryPrompt(String originalQuery) {
        return """
                You are an AI assistant helping to improve search results.
                Given the user query, generate 3 alternative phrasings that capture
                different aspects or perspectives of the same information need.

                Original query: %s

                Return only the 3 alternative queries, one per line. Do not number them.
                """.formatted(originalQuery);
    }

    private String buildHydePrompt(String query) {
        return """
                Given the following question, write a detailed paragraph that would
                contain the answer. This is a hypothetical document - write it as if
                it were an excerpt from an internal knowledge base article.

                Question: %s

                Hypothetical Document:
                """.formatted(query);
    }

    private List<String> parseAlternativeQueries(String response, String originalQuery) {
        Set<String> alternatives = new LinkedHashSet<>();

        Arrays.stream(response.split("\\R"))
                .map(this::normalizeAlternativeQuery)
                .filter(candidate -> !candidate.isBlank())
                .filter(candidate -> !candidate.equalsIgnoreCase(originalQuery.trim()))
                .forEach(candidate -> {
                    if (alternatives.size() >= ALTERNATIVE_QUERY_COUNT || containsIgnoreCase(alternatives, candidate)) {
                        return;
                    }
                    alternatives.add(candidate);
                });

        return List.copyOf(alternatives);
    }

    private String normalizeAlternativeQuery(String candidate) {
        return candidate
                .replaceFirst("^[-*•\\d.\\)\\s]+", "")
                .replaceAll("^\"|\"$", "")
                .trim();
    }

    private boolean containsIgnoreCase(Set<String> values, String candidate) {
        return values.stream().anyMatch(existing -> existing.equalsIgnoreCase(candidate));
    }
}
```

## Technique 1: Multi-Query Generation

### How It Works

**Input:** "How do I connect remotely?"

**Prompt to LLM:**
```
You are an AI assistant helping to improve search results.
Given the user query, generate 3 alternative phrasings that capture
different aspects or perspectives of the same information need.

Original query: How do I connect remotely?

Return only the 3 alternative queries, one per line. Do not number them.
```

**LLM Response:**
```
What are the steps for remote access?
How can I set up a VPN connection?
What is the process for working from home with secure access?
```

**Result:** Now we have 4 queries total (original + 3 alternatives) to search with.

### Why This Helps

Different phrasings capture different aspects:
- "remote access" → might match IT policy documents
- "VPN connection" → matches technical setup guides
- "working from home" → matches employee handbooks

By searching all variants, we increase the chance of finding relevant documents.

### Implementation Details

**Parsing LLM Output:**

```java
private List<String> parseAlternativeQueries(String response, String originalQuery) {
    Set<String> alternatives = new LinkedHashSet<>();

    Arrays.stream(response.split("\\R"))  // Split by newlines (\\R matches any line break)
            .map(this::normalizeAlternativeQuery)
            .filter(candidate -> !candidate.isBlank())
            .filter(candidate -> !candidate.equalsIgnoreCase(originalQuery.trim()))
            .forEach(candidate -> {
                if (alternatives.size() >= ALTERNATIVE_QUERY_COUNT || containsIgnoreCase(alternatives, candidate)) {
                    return;
                }
                alternatives.add(candidate);
            });

    return List.copyOf(alternatives);
}
```

**Why LinkedHashSet?**
- Preserves insertion order (important for ranking)
- Automatically deduplicates (LLM might generate similar variants)

**Normalization:**

```java
private String normalizeAlternativeQuery(String candidate) {
    return candidate
            .replaceFirst("^[-*•\\d.\\)\\s]+", "")  // Remove bullet points, numbers
            .replaceAll("^\"|\"$", "")               // Remove surrounding quotes
            .trim();
}
```

LLMs sometimes format responses as numbered lists or with bullets. This regex cleans them up.

### Error Handling

```java
try {
    String response = llm.chat(buildMultiQueryPrompt(originalQuery));
    List<String> alternatives = parseAlternativeQueries(response, originalQuery);
    return alternatives;
} catch (RuntimeException e) {
    log.warn("Multi-query generation failed for query: {}", originalQuery, e);
    return List.of();  // Return empty list, RAG pipeline continues with original query only
}
```

If the LLM call fails (network issue, rate limit, etc.), we gracefully degrade - the pipeline continues with just the original query. RAG still works, just with potentially lower recall.

## Technique 2: HyDE (Hypothetical Document Embeddings)

### How It Works

**Input:** "What is the password reset process?"

**Prompt to LLM:**
```
Given the following question, write a detailed paragraph that would
contain the answer. This is a hypothetical document - write it as if
it were an excerpt from an internal knowledge base article.

Question: What is the password reset process?

Hypothetical Document:
```

**LLM Response:**
```
The password reset process allows users to regain access to their accounts when they
forget their credentials. To reset your password, navigate to the login page and click
the "Forgot Password" link. Enter your email address and submit the form. You will
receive an automated email containing a secure reset link valid for 24 hours. Click
the link and enter your new password twice to confirm. Passwords must be at least
12 characters and include uppercase, lowercase, numbers, and special characters.
```

**Usage:** This hypothetical document is embedded and used for **vector search only**, not keyword search (we don't want to match on the detailed prose, just the semantic similarity).

### Why This Helps

Short queries don't embed well. They lack context, so their vector representation is vague.

**Example:**

```
Query: "password reset"
Embedding: [0.12, -0.05, 0.33, ...] (384 dimensions, somewhat sparse)

Hypothetical doc: "The password reset process allows users to regain access..."
Embedding: [0.18, -0.08, 0.41, ...] (384 dimensions, richer representation)
```

The hypothetical document has more semantic density. It's closer in embedding space to actual documentation about password resets.

Think of it like this: instead of asking "Where are the keys?", you describe what you're looking for: "I'm looking for small metal objects used to unlock doors, possibly hanging on a hook or in a drawer." More descriptive = better match.

### When HyDE Helps Most

**Good for:**
- Short, ambiguous queries ("deployment", "VPN", "API")
- Questions without technical terminology ("How do I X?")
- Queries where you want conceptual matches

**Less useful for:**
- Queries with specific terms that should be matched exactly ("SEV1 incident", "Error code 404")
- Queries that are already detailed

### Implementation Details

```java
public String generateHypotheticalDocument(String query) {
    try {
        String hypothetical = llm.chat(buildHydePrompt(query)).trim();
        if (hypothetical.isBlank()) {
            log.debug("HyDE returned blank output for query: {}", query);
            return query;  // Fall back to original query
        }
        log.debug("HyDE generated hypothetical document for: {}", query);
        return hypothetical;
    } catch (RuntimeException e) {
        log.warn("HyDE generation failed for query: {}", query, e);
        return query;  // Fall back to original query
    }
}
```

**Fallback strategy:** If HyDE fails or returns blank, we use the original query. This ensures the RAG pipeline never breaks due to HyDE failures.

### Using HyDE in the Pipeline

From `RAGService`:

```java
if (useQueryExpansion) {
    List<String> alternatives = queryTransformer.multiQuery(userQuestion);
    queries.addAll(alternatives);

    hypotheticalDocument = queryTransformer.generateHypotheticalDocument(userQuestion);
}

// Later, for HyDE:
if (useQueryExpansion && shouldUseHyde(hypotheticalDocument, userQuestion)) {
    List<TextSegment> hydeResults = searchService.vectorOnlySearch(hypotheticalDocument, DEFAULT_TOP_K);
    allResults.addAll(hydeResults);
}
```

**Key insight:** HyDE is used for *vector-only* search, not hybrid search. Why? Because feeding the verbose hypothetical doc to keyword search would create false matches on common words.

## Cost-Benefit Analysis

### Multi-Query

**Cost:**
- 1 extra LLM call per query (typically 50-100ms)
- 3x more retrieval operations (queries)

**Benefit:**
- 30-50% increase in recall (finds more relevant docs)
- Handles vocabulary mismatch

**When to use:**
- Critical queries where recall matters
- Diverse document vocabulary
- User queries tend to be short or ambiguous

### HyDE

**Cost:**
- 1 extra LLM call per query
- 1 more retrieval operation

**Benefit:**
- Better semantic matching for short queries
- 10-20% improvement in top result relevance

**When to use:**
- Short queries (1-3 words)
- Conceptual searches
- When embeddings of queries are poor quality

### Combined Cost

If both enabled:
- 2 extra LLM calls
- ~200-300ms latency increase
- More LLM API costs

Trade-off: Higher quality results vs. speed and cost.

## Prompt Engineering Best Practices

### Multi-Query Prompt

**What works:**
```
Generate 3 alternative phrasings that capture different aspects or perspectives
```

**Why:**
- "different aspects" → encourages diversity
- "3 alternative" → specific count, easy to parse
- "one per line" → structured output

**What doesn't work:**
```
Come up with some other ways to ask this question
```

**Why:**
- "some" is vague (could be 1, could be 10)
- "other ways" doesn't emphasize diversity
- No format specification

### HyDE Prompt

**What works:**
```
Write a detailed paragraph that would contain the answer.
This is a hypothetical document - write it as if it were an excerpt
from an internal knowledge base article.
```

**Why:**
- "detailed paragraph" → generates substantial text for embedding
- "hypothetical document" → frames the task correctly
- "knowledge base article" → sets the style/tone

**What doesn't work:**
```
What would the answer be?
```

**Why:**
- Too short, won't generate enough text
- Doesn't emphasize document-style writing

## Testing Query Transformation

```java
@Test
void testMultiQueryGeneratesAlternatives() {
    QueryTransformer transformer = new QueryTransformer(mockLlm);

    when(mockLlm.chat(anyString())).thenReturn(
        "What are the remote access steps?\n" +
        "How to configure VPN?\n" +
        "Remote work connection guide"
    );

    List<String> alternatives = transformer.multiQuery("How do I connect remotely?");

    assertEquals(3, alternatives.size());
    assertTrue(alternatives.contains("What are the remote access steps?"));
}

@Test
void testHydeGeneratesDocument() {
    QueryTransformer transformer = new QueryTransformer(mockLlm);

    when(mockLlm.chat(anyString())).thenReturn(
        "The password reset process allows users to regain access..."
    );

    String hyde = transformer.generateHypotheticalDocument("password reset");

    assertTrue(hyde.length() > 50);  // Should be substantial text
    assertTrue(hyde.contains("password"));
}

@Test
void testGracefulDegradationOnFailure() {
    QueryTransformer transformer = new QueryTransformer(mockLlm);

    when(mockLlm.chat(anyString())).thenThrow(new RuntimeException("API error"));

    List<String> alternatives = transformer.multiQuery("test query");
    assertEquals(0, alternatives.size());  // Returns empty, doesn't throw
}
```

## Key Takeaways

1. **Query expansion increases recall**: More phrasings = more chances to find relevant docs
2. **HyDE improves embedding quality**: Longer, richer text embeds better than short queries
3. **Graceful degradation is critical**: Always have fallbacks when LLM calls fail
4. **Prompt engineering matters**: Clear, specific prompts produce better results
5. **Use HyDE for vector search only**: Don't pollute keyword search with verbose prose
6. **Trade-offs exist**: Better results cost latency and API calls

## What's Next?

Now that we can transform queries, we need to retrieve documents. Let's implement keyword search using BM25.

---

**Next Chapter**: [05 - Keyword Search Service](./05-keyword-search-service.md)
