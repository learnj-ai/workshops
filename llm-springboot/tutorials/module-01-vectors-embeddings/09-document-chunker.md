# Chapter: DocumentChunker - Splitting Documents

## Introduction

**DocumentChunker** breaks large documents into smaller, embeddable chunks using different strategies.

## Code

```java
@Service
public class DocumentChunker {
    public List<TextSegment> recursiveChunk(Document document, int chunkSize, int overlap) {
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, overlap);
        return splitter.split(document);
    }

    public List<TextSegment> paragraphChunk(Document document) {
        String[] paragraphs = document.text().split("\\R\\R+");
        // Process each paragraph, splitting long ones by sentences
        // ...
    }
}
```

## Key Points

- **RECURSIVE**: Fixed-size chunks with overlap (300 chars, 30 overlap)
- **PARAGRAPH**: Natural boundaries, semantic coherence
- **Used by VectorStoreService** during index building
- **Strategy choice** affects search quality

## Next Steps

Learn how **SimilarityCalculator** compares vectors mathematically.
