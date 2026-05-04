package com.techcorp.assistant.module04.memory;

import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ConversationMemoryService.
 */
class ConversationMemoryServiceTest {

    private ConversationMemoryService memoryService;
    private RedisChatMemoryStore mockStore;

    @BeforeEach
    void setUp() {
        RedisTemplate<String, Object> mockRedis = mock(RedisTemplate.class);
        mockStore = new RedisChatMemoryStore(mockRedis);
        memoryService = new ConversationMemoryService(mockStore);
    }

    @Test
    void testGetOrCreateMemory_CreatesNew() {
        // When
        ChatMemory memory = memoryService.getOrCreateMemory("session-1");

        // Then
        assertThat(memory).isNotNull();
    }

    @Test
    void testGetOrCreateMemory_ReusesExisting() {
        // Given
        ChatMemory first = memoryService.getOrCreateMemory("session-1");

        // When
        ChatMemory second = memoryService.getOrCreateMemory("session-1");

        // Then
        assertThat(first).isSameAs(second);
    }

    @Test
    void testClearMemory() {
        // Given
        memoryService.getOrCreateMemory("session-1");

        // When
        memoryService.clearMemory("session-1");

        // Then
        int count = memoryService.getMessageCount("session-1");
        assertThat(count).isEqualTo(0);
    }
}
