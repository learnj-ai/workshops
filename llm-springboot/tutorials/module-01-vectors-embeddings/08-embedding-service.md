# Chapter: EmbeddingService - Converting Text to Vectors

## Introduction

**EmbeddingService** is the translator that converts human language into mathematical vectors. It wraps the embedding model configured in EmbeddingConfiguration and provides a clean interface for the rest of the application.

## Code

```java
@Service
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Embedding generateEmbedding(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content();
    }

    public float[] getVector(String text) {
        return generateEmbedding(text).vector();
    }

    public int dimension() {
        return embeddingModel.dimension();
    }
}
```

## Key Points

- **Wraps the embedding model** for easy dependency injection
- **generateEmbedding()** returns full Embedding object with metadata
- **getVector()** returns just the float array for convenience
- **dimension()** reports vector size (384 for AllMiniLmL6V2)
- Used by **VectorStoreService** to embed both documents and queries

## Next Steps

Learn how **DocumentChunker** prepares text for embedding.
