# Chapter: VectorStoreService - The Orchestrator

## Introduction

**VectorStoreService** is the heart of the system, coordinating document loading, chunking, embedding, indexing, and searching.

## Code

```java
@Service
public class VectorStoreService {
    private final EmbeddingService embeddingService;
    private final SimilarityCalculator similarityCalculator;
    private final DocumentChunker documentChunker;
    private final TechCorpDocumentLoader documentLoader;
    private final Map<ChunkingStrategy, List<IndexedSegment>> indexes = new EnumMap<>(ChunkingStrategy.class);

    @PostConstruct
    protected void initialize() {
        List<Document> documents = documentLoader.loadDocuments();
        for (ChunkingStrategy strategy : ChunkingStrategy.values()) {
            List<IndexedSegment> index = buildIndex(documents, strategy);
            indexes.put(strategy, index);
        }
    }

    public List<SearchMatch> search(String query, int maxResults, SearchMetric metric, ChunkingStrategy strategy) {
        Embedding queryEmbedding = embeddingService.generateEmbedding(query);
        
        return indexes.getOrDefault(strategy, List.of()).stream()
                .map(segment -> toSearchMatch(segment, queryEmbedding, metric))
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(maxResults)
                .toList();
    }
}
```

## Key Points

- **Initializes on startup** (@PostConstruct) by building indexes
- **Maintains separate indexes** for each chunking strategy
- **Orchestrates all dependencies**: loader, chunker, embedder, calculator
- **In-memory search** for fast retrieval
- **Core business logic** of the vector search system

## Next Steps

Learn how **VectorSearchController** exposes this as a REST API.
