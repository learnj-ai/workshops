package com.techcorp.assistant.module06.cache;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
