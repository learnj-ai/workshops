# Chapter: VectorSearchController - The REST API

## Introduction

**VectorSearchController** exposes the vector search functionality as an HTTP REST API endpoint.

## Code

```java
@RestController
@RequestMapping("/api/v1/search")
public class VectorSearchController {
    private final VectorStoreService vectorStoreService;

    @PostMapping("/vector")
    public ResponseEntity<SearchResponse> vectorSearch(@Valid @RequestBody SearchRequest request) {
        List<SearchResult> results = vectorStoreService
                .search(request.query(), request.maxResults(), request.metric(), request.chunkingStrategy())
                .stream()
                .map(VectorSearchController::toSearchResult)
                .toList();

        SearchResponse response = new SearchResponse(
                vectorStoreService.embeddingDimension(),
                request.metric(),
                request.chunkingStrategy(),
                vectorStoreService.indexedSegmentCount(request.chunkingStrategy()),
                results);

        return ResponseEntity.ok(response);
    }

    private static SearchResult toSearchResult(SearchMatch match) {
        return new SearchResult(match.content(), match.score(), match.metadata().toMap());
    }
}
```

## Key Points

- **POST /api/v1/search/vector** endpoint
- **@Valid annotation** triggers request validation
- **Transforms SearchMatch** to SearchResult (API DTO)
- **Returns SearchResponse** with metadata
- **Delegates to VectorStoreService** for business logic

## Next Steps

Learn how **GlobalExceptionHandler** handles errors gracefully.
