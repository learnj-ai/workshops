# Chapter: CachingService - Semantic Caching with Redis

## Introduction

**CachingService** implements semantic caching—a technique that returns cached responses for *semantically similar* queries, not just exact matches. This dramatically reduces LLM API costs and improves response times for common question variations.

Traditional caching only helps if users ask the exact same question. Semantic caching helps when they ask "How do I reset my password?" after you've already answered "What's the password reset process?"

## Code

```java
@Service
public class CachingService {

    private static final Logger log = LoggerFactory.getLogger(CachingService.class);
    private static final String SEMANTIC_CACHE_PREFIX = "semantic:";

    private final RedisTemplate<String, String> redisTemplate;
    private final EmbeddingModel embeddingModel;
    private final Map<String, Embedding> embeddingCache;

    @Value("${semantic-cache.similarity-threshold:0.95}")
    private double similarityThreshold;

    @Value("${semantic-cache.ttl-seconds:3600}")
    private long ttlSeconds;

    public CachingService(RedisTemplate<String, String> redisTemplate, EmbeddingModel embeddingModel) {
        this.redisTemplate = redisTemplate;
        this.embeddingModel = embeddingModel;
        this.embeddingCache = new HashMap<>();
    }

    @Cacheable(value = "exactQueryCache", key = "#query")
    public String exactCacheGet(String query) {
        log.debug("Exact cache miss for: {}", query);
        return null; // Cache miss handled by @Cacheable
    }

    public String semanticCacheGet(String query) {
        // Get or compute embedding for query
        Embedding queryEmbedding = getOrComputeEmbedding(query);

        // Search for similar cached queries
        Map<Object, Object> cache = redisTemplate.opsForHash().entries(SEMANTIC_CACHE_PREFIX + "queries");

        for (Map.Entry<Object, Object> entry : cache.entrySet()) {
            String cachedQuery = (String) entry.getKey();
            Embedding cachedEmbedding = getOrComputeEmbedding(cachedQuery);

            double similarity = cosineSimilarity(queryEmbedding, cachedEmbedding);

            if (similarity >= similarityThreshold) {
                String response = (String) entry.getValue();
                log.info("Semantic cache hit - similarity: {}", similarity);
                return response;
            }
        }

        log.debug("Semantic cache miss for: {}", query);
        return null;
    }

    public void semanticCachePut(String query, String response) {
        // Store in semantic cache
        redisTemplate.opsForHash().put(SEMANTIC_CACHE_PREFIX + "queries", query, response);
        redisTemplate.expire(SEMANTIC_CACHE_PREFIX + "queries", ttlSeconds, TimeUnit.SECONDS);

        // Cache embedding
        getOrComputeEmbedding(query);

        log.debug("Cached response for query: {}", query);
    }

    private Embedding getOrComputeEmbedding(String text) {
        return embeddingCache.computeIfAbsent(text, key -> {
            log.debug("Computing embedding for: {}", key);
            return embeddingModel.embed(key).content();
        });
    }

    private double cosineSimilarity(Embedding a, Embedding b) {
        float[] vectorA = a.vector();
        float[] vectorB = b.vector();

        if (vectorA.length != vectorB.length) {
            throw new IllegalArgumentException("Vectors must have same length");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
```

## Key Concepts

### Two-Level Caching

**Exact Match Cache** (`@Cacheable`):
- Spring's annotation-based caching
- Only matches identical queries
- Fastest path (no embedding computation)
- Key: exact query string

**Semantic Cache** (Redis + embeddings):
- Matches similar queries even with different wording
- Requires embedding computation for similarity check
- Key: query string, Value: response
- Separate in-memory cache for embeddings

### Semantic Similarity Threshold

```java
@Value("${semantic-cache.similarity-threshold:0.95}")
private double similarityThreshold;
```

**Cosine similarity ranges from -1 to 1**:
- `1.0`: Identical meaning (parallel vectors)
- `0.95-0.99`: Very similar (typical threshold for caching)
- `0.8-0.95`: Moderately similar
- `< 0.8`: Different topics

**Choosing a threshold**:
- **Too high (0.99)**: Misses beneficial cache hits, only catches near-duplicates
- **Too low (0.8)**: Returns cached responses for unrelated queries
- **Recommended (0.95)**: Balances cache hit rate with response accuracy

### Cosine Similarity Implementation

Measures the angle between two vectors:

```java
private double cosineSimilarity(Embedding a, Embedding b) {
    float[] vectorA = a.vector();
    float[] vectorB = b.vector();

    double dotProduct = 0.0;   // a · b = Σ(ai × bi)
    double normA = 0.0;         // ||a|| = √Σ(ai²)
    double normB = 0.0;         // ||b|| = √Σ(bi²)

    for (int i = 0; i < vectorA.length; i++) {
        dotProduct += vectorA[i] * vectorB[i];
        normA += vectorA[i] * vectorA[i];
        normB += vectorB[i] * vectorB[i];
    }

    // cos(θ) = (a · b) / (||a|| × ||b||)
    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
}
```

**Why cosine similarity?**
- Independent of vector magnitude (focuses on direction)
- Widely used for text similarity
- Numerically stable
- Matches how embedding models are typically trained

### Embedding Cache

```java
private final Map<String, Embedding> embeddingCache;

private Embedding getOrComputeEmbedding(String text) {
    return embeddingCache.computeIfAbsent(text, key -> {
        log.debug("Computing embedding for: {}", key);
        return embeddingModel.embed(key).content();
    });
}
```

**Why cache embeddings in memory?**
- Computing embeddings is expensive (10-50ms per query)
- Cached queries are checked repeatedly
- In-memory cache avoids redundant computation
- Redis stores responses, Java Map stores embeddings

**Memory considerations**:
- Each embedding: ~1.5KB (384 floats × 4 bytes)
- 1000 cached queries: ~1.5MB
- 10,000 cached queries: ~15MB
- Manageable for most applications

### TTL (Time To Live)

```java
@Value("${semantic-cache.ttl-seconds:3600}")
private long ttlSeconds;

redisTemplate.expire(SEMANTIC_CACHE_PREFIX + "queries", ttlSeconds, TimeUnit.SECONDS);
```

**Why TTL?**
- Prevents stale responses (documentation updates, policy changes)
- Manages Redis memory usage
- Balances freshness with cache efficiency

**Typical TTL values**:
- **High volatility** (pricing, inventory): 5-15 minutes
- **Medium volatility** (product info, FAQs): 1-6 hours
- **Low volatility** (documentation, policies): 12-24 hours

## Usage Flow

### Cache Lookup

```java
public ResponseEntity<RAGResponse> query(@RequestBody QueryRequest request) {
    // 1. Check exact match cache (fastest)
    String exactMatch = cachingService.exactCacheGet(request.query());
    if (exactMatch != null) {
        return ResponseEntity.ok(new RAGResponse(exactMatch, true));
    }

    // 2. Check semantic cache
    String semanticMatch = cachingService.semanticCacheGet(request.query());
    if (semanticMatch != null) {
        return ResponseEntity.ok(new RAGResponse(semanticMatch, true));
    }

    // 3. Cache miss - execute RAG query
    SimpleRAGService.RAGResponse response = ragService.query(request.query());

    // 4. Store in both caches
    cachingService.semanticCachePut(request.query(), response.response());

    return ResponseEntity.ok(new RAGResponse(response.response(), false));
}
```

### Performance Characteristics

**Exact cache hit**:
- Latency: ~1-5ms (Spring cache + Redis lookup)
- Cost: $0 (no LLM call)

**Semantic cache hit**:
- Latency: ~20-50ms (embedding + similarity calculation + Redis lookup)
- Cost: Small (embedding only, no LLM completion)

**Cache miss**:
- Latency: ~200-2000ms (full RAG pipeline + LLM)
- Cost: Full LLM API call

## Configuration

### application.properties

```properties
# Semantic cache configuration
semantic-cache.enabled=true
semantic-cache.similarity-threshold=0.95
semantic-cache.ttl-seconds=3600

# Redis configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000
```

### Production Tuning

**High-traffic applications**:
```properties
semantic-cache.similarity-threshold=0.96  # Stricter to reduce false positives
semantic-cache.ttl-seconds=1800           # 30 minutes
```

**Documentation/FAQ bots**:
```properties
semantic-cache.similarity-threshold=0.93  # More lenient for question variations
semantic-cache.ttl-seconds=7200           # 2 hours (stable content)
```

**Cost-sensitive applications**:
```properties
semantic-cache.similarity-threshold=0.90  # Very lenient to maximize cache hits
semantic-cache.ttl-seconds=3600
```

## Monitoring Cache Performance

Track these metrics to optimize your cache:

```java
@Service
public class CachingService {

    private final Counter exactHits;
    private final Counter semanticHits;
    private final Counter cacheMisses;
    private final Timer embeddingTime;

    public CachingService(MeterRegistry registry, ...) {
        this.exactHits = Counter.builder("cache.hits")
            .tag("type", "exact")
            .register(registry);

        this.semanticHits = Counter.builder("cache.hits")
            .tag("type", "semantic")
            .register(registry);

        this.cacheMisses = Counter.builder("cache.misses")
            .register(registry);

        this.embeddingTime = Timer.builder("cache.embedding.time")
            .register(registry);
    }

    public String semanticCacheGet(String query) {
        Timer.Sample sample = Timer.start(registry);

        Embedding queryEmbedding = getOrComputeEmbedding(query);

        sample.stop(embeddingTime);

        // ... similarity search ...

        if (similarity >= similarityThreshold) {
            semanticHits.increment();
            return response;
        }

        cacheMisses.increment();
        return null;
    }
}
```

**Key metrics**:
- **Cache hit rate**: `cache.hits / (cache.hits + cache.misses)`
- **Semantic hit rate**: `semantic_hits / total_hits`
- **Embedding latency**: `cache.embedding.time`
- **Cost savings**: `cache.hits × avg_llm_cost`

## Advanced Optimizations

### Pre-warming Cache

Populate cache with common queries on startup:

```java
@Component
public class CacheWarmer {

    @PostConstruct
    public void warmCache() {
        List<String> commonQueries = List.of(
            "How do I reset my password?",
            "What are your business hours?",
            "How do I contact support?"
        );

        for (String query : commonQueries) {
            if (cachingService.semanticCacheGet(query) == null) {
                String response = ragService.query(query).response();
                cachingService.semanticCachePut(query, response);
            }
        }
    }
}
```

### Approximate Nearest Neighbor (ANN)

For large caches, use ANN indexes (e.g., Redis Vector Search) instead of linear scan:

```java
// With Redis Vector Search module
public String semanticCacheGetFast(String query) {
    Embedding queryEmbedding = getOrComputeEmbedding(query);

    // Use KNN search instead of scanning all entries
    List<VectorSearchResult> results = redisVectorSearch.knn(
        "semantic:embeddings",
        queryEmbedding.vector(),
        1,  // top 1 result
        similarityThreshold
    );

    if (!results.isEmpty() && results.get(0).score() >= similarityThreshold) {
        return results.get(0).response();
    }

    return null;
}
```

### Multi-Tier Caching

Combine in-memory and Redis caches:

```java
// L1: In-memory cache (fastest, small capacity)
private final Map<String, String> l1Cache = new ConcurrentHashMap<>();

// L2: Redis cache (slower, larger capacity)
private final RedisTemplate<String, String> redisTemplate;

public String get(String query) {
    // Check L1
    String response = l1Cache.get(query);
    if (response != null) {
        return response;
    }

    // Check L2
    response = semanticCacheGet(query);
    if (response != null) {
        l1Cache.put(query, response);  // Promote to L1
        return response;
    }

    return null;
}
```

## Best Practices

**Start with exact caching, add semantic caching incrementally**:
- Exact caching is simpler and faster
- Add semantic caching when you see query variations

**Monitor cache hit rates by threshold**:
- Experiment with different thresholds in production
- A/B test to find optimal value for your domain

**Use TTL aggressively**:
- Prevents serving stale information
- Manages memory usage automatically

**Log cache decisions**:
- Record similarity scores for cache hits
- Helps tune threshold based on real traffic

**Validate cache quality periodically**:
- Sample semantic cache hits and verify responses are still appropriate
- Implement feedback mechanism to invalidate bad caches

## Testing

```java
@Test
void shouldReturnCachedResponseForSimilarQuery() {
    // Given: Cache populated with a query
    String originalQuery = "How do I reset my password?";
    String response = "To reset your password, click Forgot Password...";
    cachingService.semanticCachePut(originalQuery, response);

    // When: Similar query is made
    String similarQuery = "What's the password reset process?";
    String cachedResponse = cachingService.semanticCacheGet(similarQuery);

    // Then: Cached response is returned
    assertThat(cachedResponse).isEqualTo(response);
}

@Test
void shouldNotReturnCacheForDifferentQuery() {
    // Given: Cache populated
    cachingService.semanticCachePut("How do I reset my password?", "...");

    // When: Unrelated query is made
    String unrelatedQuery = "What are your business hours?";
    String cachedResponse = cachingService.semanticCacheGet(unrelatedQuery);

    // Then: Cache miss
    assertThat(cachedResponse).isNull();
}
```

## Key Takeaways

- **Semantic caching reduces costs and latency** by reusing responses for similar questions
- **Cosine similarity measures semantic closeness** between query embeddings
- **Threshold tuning balances cache hit rate with response accuracy** (recommended: 0.95)
- **Two-level caching provides fast exact matches and flexible semantic matches**
- **TTL prevents stale responses** and manages Redis memory usage
- **Monitoring cache metrics enables continuous optimization** of threshold and TTL

## Next Steps

Learn how **RedisConfig** sets up the Redis infrastructure for distributed caching.

---

**Next Chapter**: [06 - RedisConfig: Redis Infrastructure Setup](./06-redis-config.md)
