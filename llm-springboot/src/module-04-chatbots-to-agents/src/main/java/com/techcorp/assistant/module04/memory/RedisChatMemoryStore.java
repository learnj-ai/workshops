package com.techcorp.assistant.module04.memory;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Redis-backed implementation of ChatMemoryStore.
 *
 * Stores conversation history in Redis with TTL for automatic expiration.
 *
 * NOTE: This is a simplified workshop implementation. In production,
 * you would properly serialize/deserialize ChatMessage objects to/from JSON.
 */
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);
    private static final String MEMORY_KEY_PREFIX = "chat:memory:";
    private static final long TTL_HOURS = 24;

    // In-memory cache for workshop simplicity
    // Production would use proper Redis serialization
    private final java.util.Map<String, List<ChatMessage>> memoryCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final RedisTemplate<String, Object> redisTemplate;

    public RedisChatMemoryStore(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = memoryId.toString();
        List<ChatMessage> messages = memoryCache.get(key);

        if (messages == null) {
            log.debug("No messages found for memory ID: {}", memoryId);
            return new ArrayList<>();
        }

        log.debug("Retrieved {} messages for memory ID: {}", messages.size(), memoryId);
        return new ArrayList<>(messages);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = memoryId.toString();

        if (messages == null || messages.isEmpty()) {
            memoryCache.remove(key);
            return;
        }

        // Store in memory cache
        memoryCache.put(key, new ArrayList<>(messages));

        // Mark as stored in Redis (simplified for workshop)
        String redisKey = MEMORY_KEY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, "stored", Duration.ofHours(TTL_HOURS));

        log.debug("Stored {} messages for memory ID: {} with TTL of {} hours",
                messages.size(), memoryId, TTL_HOURS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        String key = memoryId.toString();
        memoryCache.remove(key);

        String redisKey = MEMORY_KEY_PREFIX + key;
        redisTemplate.delete(redisKey);

        log.debug("Deleted messages for memory ID: {}", memoryId);
    }
}
