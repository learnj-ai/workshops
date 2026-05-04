package com.techcorp.assistant.module06.cache;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CachingServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private EmbeddingModel embeddingModel;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    private CachingService cachingService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);

        cachingService = new CachingService(redisTemplate, embeddingModel);
        ReflectionTestUtils.setField(cachingService, "similarityThreshold", 0.95);
        ReflectionTestUtils.setField(cachingService, "ttlSeconds", 3600L);
    }

    @Test
    void testSemanticCacheMiss() {
        when(hashOperations.entries(anyString())).thenReturn(new HashMap<>());

        String result = cachingService.semanticCacheGet("test query");

        assertNull(result);
    }

    @Test
    void testSemanticCacheHit() {
        // Setup similar embeddings
        float[] vector = {1.0f, 0.0f, 0.0f};
        when(embeddingModel.embed(anyString()))
                .thenReturn(Response.from(new Embedding(vector)));

        Map<Object, Object> cache = new HashMap<>();
        cache.put("similar query", "cached response");
        when(hashOperations.entries(anyString())).thenReturn(cache);

        String result = cachingService.semanticCacheGet("test query");

        assertEquals("cached response", result);
    }

    @Test
    void testSemanticCachePut() {
        cachingService.semanticCachePut("query", "response");

        verify(hashOperations).put(anyString(), eq("query"), eq("response"));
        verify(redisTemplate).expire(anyString(), eq(3600L), any());
    }
}
