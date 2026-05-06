# Chapter: SearchMetric - Measuring Vector Similarity

## Introduction: Different Ways to Measure Distance

Imagine you're comparing two cities. You could measure:
- **Straight-line distance** (as the crow flies)
- **Driving distance** (following roads)
- **Angular difference** (comparing directions from a reference point)

Each measurement gives different insights. Similarly, **SearchMetric** defines three mathematical approaches for comparing vectors: COSINE, EUCLIDEAN, and DOT_PRODUCT. Each has unique characteristics that make it suitable for different scenarios.

## How It Works: Three Similarity Metrics

```java
package com.techcorp.assistant.similarity;

public enum SearchMetric {
    COSINE,
    EUCLIDEAN,
    DOT_PRODUCT
}
```

### COSINE Similarity
- Measures the **angle** between vectors (ignores magnitude)
- Range: -1 (opposite) to 1 (identical)
- **Best for**: Text embeddings (most common choice)
- **Why**: Text embeddings are normalized, so direction matters more than magnitude

### EUCLIDEAN Distance
- Measures the **straight-line distance** between vector endpoints
- Range: 0 (identical) to ∞ (very different)
- **Best for**: When magnitude matters (e.g., comparing feature vectors with different scales)

### DOT PRODUCT
- Measures both **angle and magnitude**
- Range: -∞ to +∞
- **Best for**: Pre-normalized vectors where you want fast computation

## Code Deep Dive: SimilarityCalculator

```java
@Component
public class SimilarityCalculator {

    public double cosineSimilarity(float[] vectorA, float[] vectorB) {
        validateDimensions(vectorA, vectorB);
        
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) return 0.0;
        
        return dotProduct / Math.sqrt(normA * normB);
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

**Note**: Euclidean distance is negated so higher scores = more similar (consistent with other metrics).

## Relationships

- **SearchRequest** specifies which metric to use
- **SimilarityCalculator** implements all three metric calculations
- **VectorStoreService** uses the calculator to score matches
- **SearchMatch** contains the similarity score

## Key Takeaways

- **SearchMetric defines three mathematical approaches** for comparing vectors
- **COSINE is most common for text** because it measures semantic similarity regardless of length
- **Higher scores always mean better matches** across all metrics
- **Choice of metric can significantly impact results**—experiment to find what works best
- **The metric is applied during search**, not during indexing

## Next Steps

Next, you'll learn about **EmbeddingService**—the component that actually generates the vector embeddings that these metrics compare.
