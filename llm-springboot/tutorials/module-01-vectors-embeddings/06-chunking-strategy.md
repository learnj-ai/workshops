# Chapter: ChunkingStrategy - Choosing How to Split Documents

## Introduction: Cutting a Pizza

Imagine you're serving pizza at a party. You could cut it into equal-sized triangular slices (recursive, uniform approach) or you could cut it based on toppings—keeping sections with similar toppings together (paragraph-based, semantic approach). Both work, but they serve different purposes.

**ChunkingStrategy** is exactly this choice for documents. Should we split text into fixed-size chunks with overlap (RECURSIVE), or should we respect natural boundaries like paragraphs (PARAGRAPH)? The strategy you choose affects search quality, context preservation, and result relevance.

## How It Works: Strategy Pattern

```java
package com.techcorp.assistant.chunking;

public enum ChunkingStrategy {
    RECURSIVE,
    PARAGRAPH
}
```

This simple enum defines two approaches:

### RECURSIVE Strategy
- Splits text into fixed-size chunks (e.g., 300 characters)
- Adds overlap between chunks (e.g., 30 characters)
- Ensures no chunk is too large for the embedding model
- **Best for**: Dense technical docs, code, structured content

### PARAGRAPH Strategy
- Splits on natural paragraph boundaries (double newlines)
- Keeps semantic units together
- Splits long paragraphs by sentence boundaries
- **Best for**: Narrative text, articles, policies

## Code Deep Dive: DocumentChunker Implementation

The `DocumentChunker` service implements both strategies:

```java
@Service
public class DocumentChunker {
    
    public List<TextSegment> recursiveChunk(Document document, int chunkSize, int overlap) {
        DocumentSplitter splitter = DocumentSplitters.recursive(chunkSize, overlap);
        return splitter.split(document);
    }

    public List<TextSegment> paragraphChunk(Document document) {
        String[] paragraphs = document.text().split("\\R\\R+");
        List<TextSegment> segments = new ArrayList<>();

        for (String paragraph : paragraphs) {
            String normalizedParagraph = paragraph.trim();
            if (normalizedParagraph.isEmpty()) continue;

            if (normalizedParagraph.length() > DEFAULT_MAX_PARAGRAPH_CHUNK_LENGTH) {
                segments.addAll(splitBySemanticBoundaries(normalizedParagraph));
            } else {
                segments.add(TextSegment.from(normalizedParagraph));
            }
        }
        return segments;
    }
}
```

### Strategy Selection in VectorStoreService

```java
private List<TextSegment> chunkDocument(Document document, ChunkingStrategy strategy) {
    List<TextSegment> segments = switch (strategy) {
        case RECURSIVE -> documentChunker.recursiveChunk(document, 300, 30);
        case PARAGRAPH -> documentChunker.paragraphChunk(document);
    };
    return annotateSegments(document, segments, strategy);
}
```

## Relationships

- **SearchRequest** specifies which strategy to use for a search
- **DocumentChunker** implements the splitting logic for each strategy
- **VectorStoreService** maintains separate indexes for each strategy
- **SearchResponse** reports which strategy was used

## Key Takeaways

- **ChunkingStrategy uses the Strategy pattern** to allow runtime selection of document splitting approaches
- **RECURSIVE is predictable and uniform**, good for dense technical content
- **PARAGRAPH preserves semantic boundaries**, good for narrative text
- **The system maintains separate indexes** for each strategy, allowing A/B comparison
- **Strategy choice affects search quality**: choose based on your document type

## Next Steps

Now that you understand chunking strategies, you're ready to learn about **SearchMetric**—the different mathematical approaches for measuring similarity between vectors.
