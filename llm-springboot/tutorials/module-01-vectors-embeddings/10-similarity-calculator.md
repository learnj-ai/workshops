# Chapter: SimilarityCalculator - Comparing Vectors

## Introduction

**SimilarityCalculator** performs the mathematical heavy lifting of comparing vectors using different similarity metrics.

## Code

```java
@Component
public class SimilarityCalculator {
    public double cosineSimilarity(float[] vectorA, float[] vectorB) {
        // Calculates angle between vectors
        // Returns value between -1 and 1
    }

    public double euclideanDistance(float[] vectorA, float[] vectorB) {
        // Calculates straight-line distance
        // Returns value from 0 to infinity
    }

    public double score(float[] vectorA, float[] vectorB, SearchMetric metric) {
        return switch (metric) {
            case COSINE -> cosineSimilarity(vectorA, vectorB);
            case EUCLIDEAN -> -euclideanDistance(vectorA, vectorB);
            case DOT_PRODUCT -> dotProduct(vectorA, vectorB);
        };
    }
}
```

## Key Points

- **Three algorithms**: cosine, euclidean, dot product
- **Higher scores = better matches** (euclidean negated)
- **Validates dimensions** to prevent errors
- **Used by VectorStoreService** during search

## Next Steps

Learn how **TechCorpDocumentLoader** loads source documents.
