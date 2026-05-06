# RAG Request and Response DTOs

## Overview

Every REST API needs clear contracts defining what goes in and what comes out. In our RAG system, `RAGRequest` and `RAGResponse` define the API surface for the main query endpoint.

These are simple DTOs (Data Transfer Objects), but they demonstrate important patterns: validation, sensible defaults, and immutability using Java records.

## The RAGRequest Record

```java
package com.techcorp.assistant.rag;

import jakarta.validation.constraints.NotBlank;

public record RAGRequest(
        @NotBlank String question,
        Boolean useQueryExpansion) {

    public RAGRequest {
        useQueryExpansion = (useQueryExpansion == null) ? true : useQueryExpansion;
    }
}
```

### What's Happening Here?

**Java Record**: Instead of writing a class with getters, setters, `equals()`, `hashCode()`, and `toString()`, a record gives you all of that in one line. Records are immutable by default - perfect for DTOs.

**Validation**: The `@NotBlank` annotation ensures the question isn't null, empty, or just whitespace. Spring Boot's validation framework automatically enforces this when the request arrives.

**Compact Constructor**: The block after the parameter list is a *compact constructor* - it runs after the implicit field initialization. Here we use it to provide a sensible default: if the client doesn't specify `useQueryExpansion`, we default to `true`.

### Design Decisions

**Why Boolean instead of boolean?**
- `Boolean` (nullable) lets clients omit the field entirely
- `boolean` (primitive) would require them to always send `true` or `false`
- This gives clients more flexibility

**Why default to true for query expansion?**
- Query expansion (multi-query + HyDE) improves recall in most cases
- The cost is ~2-3 extra LLM calls, which is acceptable for better results
- Advanced users can disable it for faster responses when they know the exact query works

## The RAGResponse Record

```java
package com.techcorp.assistant.rag;

public record RAGResponse(String answer) {
}
```

### Simplicity is a Feature

This is intentionally minimal. The response contains just the generated answer as a string.

**Why not include metadata?** (retrieved documents, confidence scores, etc.)

For a production system, you'd likely add:
- `List<String> sourceDocuments` - what was retrieved
- `double confidence` - how confident the LLM is
- `int retrievalTime` - how long retrieval took
- `int generationTime` - how long LLM generation took

But for learning, we keep it simple to focus on the core RAG mechanics.

### Real-World Evolution

In production, your response might evolve to:

```java
public record RAGResponse(
    String answer,
    List<SourceDocument> sources,
    Metadata metadata
) {
    public record SourceDocument(String text, String source, double score) {}
    public record Metadata(int retrievalTimeMs, int generationTimeMs, boolean usedQueryExpansion) {}
}
```

Start simple, add complexity only when needed.

## Using These DTOs

### Client Request Example

```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "How do I reset my password?",
    "useQueryExpansion": true
  }'
```

### Server Response Example

```json
{
  "answer": "To reset your password, go to the login page and click 'Forgot Password'. You'll receive an email with a reset link valid for 24 hours. Follow the link and enter your new password twice to confirm."
}
```

### Validation in Action

If a client sends an invalid request:

```bash
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{
    "question": "",
    "useQueryExpansion": true
  }'
```

Spring Boot's validation returns:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "question: must not be blank"
}
```

This happens automatically because of `@NotBlank` on the `question` field.

## Pattern: Compact Constructor for Defaults

The compact constructor pattern is powerful for providing sensible defaults:

```java
public record RAGRequest(
        @NotBlank String question,
        Boolean useQueryExpansion) {

    public RAGRequest {
        // This runs AFTER fields are assigned from constructor parameters
        // You can reassign fields here to provide defaults
        useQueryExpansion = (useQueryExpansion == null) ? true : useQueryExpansion;
    }
}
```

**Why this works:**
1. Client sends JSON: `{"question": "How to deploy?"}`
2. Jackson deserializes: `question = "How to deploy?"`, `useQueryExpansion = null`
3. Compact constructor runs: sees `null`, replaces with `true`
4. Record is now: `question = "How to deploy?"`, `useQueryExpansion = true`

**Alternative approaches:**

You could handle this in the service layer:
```java
boolean expansion = request.useQueryExpansion() != null
    ? request.useQueryExpansion()
    : true;
```

But doing it in the DTO constructor means the service layer always receives a non-null value - cleaner and less error-prone.

## Testing These DTOs

Even simple DTOs benefit from tests:

```java
@Test
void testDefaultQueryExpansion() {
    RAGRequest request = new RAGRequest("test question", null);
    assertTrue(request.useQueryExpansion());
}

@Test
void testExplicitQueryExpansion() {
    RAGRequest request = new RAGRequest("test question", false);
    assertFalse(request.useQueryExpansion());
}

@Test
void testValidationFailsOnBlankQuestion() {
    Set<ConstraintViolation<RAGRequest>> violations =
        validator.validate(new RAGRequest("", true));

    assertEquals(1, violations.size());
    assertEquals("must not be blank", violations.iterator().next().getMessage());
}
```

## Key Takeaways

1. **Records are perfect for DTOs**: Immutable, concise, less boilerplate
2. **Validate at the boundary**: Use `@NotBlank`, `@NotNull`, `@Min`, etc. to fail fast
3. **Use nullable types for optional fields**: `Boolean` instead of `boolean` allows clients to omit fields
4. **Compact constructors for defaults**: Centralize default logic in the DTO
5. **Start simple**: Add complexity (metadata, sources) only when you need it

## What's Next?

Now that we understand the API contract, let's look at how clients can compare different search strategies before committing to one.

---

**Next Chapter**: [03 - Search Comparison Response](./03-search-comparison-response.md)
