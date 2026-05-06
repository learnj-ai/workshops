# Conversation Memory

## Introduction

So far, our agents have been stateless—they answer each question independently without remembering previous interactions. But real conversations build on context. When a user asks "What's my account status?" followed by "And what about my open tickets?", the agent needs to remember which account we're talking about.

In this chapter, you'll learn how to implement **conversation memory** using LangChain4j's memory abstractions and Redis for persistence.

## Why Memory Matters

### Stateless vs. Stateful

**Stateless** (what we've built so far):
```
User: "What's the status for customer 12345?"
Agent: "Customer 12345 has an Enterprise plan..."

User: "And their open tickets?"
Agent: "I don't know which customer you're referring to."
```

**Stateful** (with memory):
```
User: "What's the status for customer 12345?"
Agent: "Customer 12345 has an Enterprise plan..."

User: "And their open tickets?"
Agent: "Customer 12345 has 3 open tickets: ..."
```

The agent remembers the context from previous turns, making conversations more natural.

## LangChain4j Memory Abstractions

LangChain4j provides two key interfaces:

### ChatMemory

Stores messages for a single conversation:
```java
public interface ChatMemory {
    void add(ChatMessage message);
    List<ChatMessage> messages();
    void clear();
}
```

**Purpose**: Holds the conversation history for a specific session.

**Implementations**:
- `MessageWindowChatMemory`: Keeps the last N messages
- `TokenWindowChatMemory`: Keeps messages up to N tokens

### ChatMemoryStore

Persists messages across application restarts:
```java
public interface ChatMemoryStore {
    List<ChatMessage> getMessages(Object memoryId);
    void updateMessages(Object memoryId, List<ChatMessage> messages);
    void deleteMessages(Object memoryId);
}
```

**Purpose**: Backend storage for memories (Redis, database, etc.).

**Our Implementation**: `RedisChatMemoryStore` persists to Redis.

## ConversationMemoryService

Our service manages session-based memories:

```java
@Service
public class ConversationMemoryService {
    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryService.class);

    private final RedisChatMemoryStore memoryStore;
    private final Map<String, ChatMemory> activeMemories = new ConcurrentHashMap<>();

    @Value("${agent.memory.max-messages:20}")
    private int maxMessages;

    public ConversationMemoryService(RedisChatMemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * Gets or creates a chat memory for the given session ID.
     */
    public ChatMemory getOrCreateMemory(String sessionId) {
        return activeMemories.computeIfAbsent(sessionId, id -> {
            log.info("Creating new memory for session: {}", id);
            return MessageWindowChatMemory.builder()
                    .id(id)
                    .maxMessages(maxMessages)
                    .chatMemoryStore(memoryStore)
                    .build();
        });
    }

    /**
     * Adds a user message to the session memory.
     */
    public void addMessage(String sessionId, String message) {
        ChatMemory memory = getOrCreateMemory(sessionId);
        memory.add(UserMessage.from(message));
        log.debug("Added user message to session {}: {}", sessionId, message);
    }

    /**
     * Adds an AI response to the session memory.
     */
    public void addAiMessage(String sessionId, String response) {
        ChatMemory memory = getOrCreateMemory(sessionId);
        memory.add(AiMessage.from(response));
        log.debug("Added AI message to session {}", sessionId);
    }

    /**
     * Gets conversation history for a session.
     */
    public List<ChatMessage> getHistory(String sessionId) {
        ChatMemory memory = getOrCreateMemory(sessionId);
        return memory.messages();
    }

    /**
     * Clears all messages for a session.
     */
    public void clearMemory(String sessionId) {
        ChatMemory memory = activeMemories.get(sessionId);
        if (memory != null) {
            memory.clear();
            activeMemories.remove(sessionId);
            log.info("Cleared memory for session: {}", sessionId);
        }
    }

    /**
     * Gets the number of messages in a session's memory.
     */
    public int getMessageCount(String sessionId) {
        ChatMemory memory = activeMemories.get(sessionId);
        return memory != null ? memory.messages().size() : 0;
    }
}
```

### Key Design Patterns

**Session-Based**: Each session (identified by a string ID) has its own `ChatMemory` instance.

**Lazy Initialization**: Memories are created on-demand via `computeIfAbsent()`.

**Message Window**: `MessageWindowChatMemory` keeps the last `maxMessages` (default: 20), preventing unbounded memory growth.

**Persistent Backend**: All memories use `RedisChatMemoryStore`, so they survive application restarts.

**Active Memory Cache**: The `activeMemories` map keeps recently used sessions in memory for fast access.

## Message Types

LangChain4j defines several message types:

### UserMessage

Represents user input:
```java
UserMessage userMsg = UserMessage.from("What's my account status?");
memory.add(userMsg);
```

### AiMessage

Represents AI/agent responses:
```java
AiMessage aiMsg = AiMessage.from("Your account is active with Enterprise plan.");
memory.add(aiMsg);
```

### SystemMessage

Sets context/instructions (usually at conversation start):
```java
SystemMessage sysMsg = SystemMessage.from("You are a helpful customer support agent.");
memory.add(sysMsg);
```

### ToolExecutionResultMessage

Records tool execution results (useful for ReAct pattern):
```java
ToolExecutionResultMessage toolMsg = ToolExecutionResultMessage.from(
    "getCustomerInfo",
    "Customer 12345: John Doe, Enterprise plan"
);
memory.add(toolMsg);
```

## RedisChatMemoryStore Implementation

Our Redis-backed memory store:

```java
@Component
public class RedisChatMemoryStore implements ChatMemoryStore {
    private static final Logger log = LoggerFactory.getLogger(RedisChatMemoryStore.class);
    private static final String MEMORY_KEY_PREFIX = "chat:memory:";
    private static final long TTL_HOURS = 24;

    // In-memory cache for workshop simplicity
    // Production would use proper Redis serialization
    private final Map<String, List<ChatMessage>> memoryCache = new ConcurrentHashMap<>();
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
```

### Design Notes

**Workshop Simplification**: This implementation uses an in-memory cache (`memoryCache`) for simplicity. In production, you'd serialize `ChatMessage` objects to JSON and store them directly in Redis.

**TTL**: Messages expire after 24 hours, preventing stale conversations from accumulating.

**Key Prefix**: All memory keys start with `chat:memory:` for easy identification in Redis.

**Idempotent Deletes**: Deleting a non-existent memory ID is safe (no-op).

### Production Implementation

For production, you'd serialize messages to JSON:

```java
@Override
public void updateMessages(Object memoryId, List<ChatMessage> messages) {
    String key = MEMORY_KEY_PREFIX + memoryId.toString();

    // Serialize to JSON
    String json = objectMapper.writeValueAsString(messages);

    // Store in Redis with TTL
    redisTemplate.opsForValue().set(key, json, Duration.ofHours(TTL_HOURS));

    log.debug("Stored {} messages for memory ID: {}", messages.size(), memoryId);
}

@Override
public List<ChatMessage> getMessages(Object memoryId) {
    String key = MEMORY_KEY_PREFIX + memoryId.toString();

    String json = (String) redisTemplate.opsForValue().get(key);

    if (json == null) {
        return new ArrayList<>();
    }

    // Deserialize from JSON
    return objectMapper.readValue(json, new TypeReference<List<ChatMessage>>() {});
}
```

## Session Management in Controller

The controller integrates memory into the agent workflow:

```java
@PostMapping("/execute")
public ResponseEntity<AgentResponse> execute(@RequestBody AgentRequest request) {
    log.info("Agent execute request - mode: {}, session: {}", request.mode(), request.sessionId());

    try {
        // Get or create session
        String sessionId = request.sessionId() != null && !request.sessionId().isBlank()
                ? request.sessionId()
                : UUID.randomUUID().toString();

        // Add user message to memory
        memoryService.addMessage(sessionId, request.message());

        // Execute based on mode
        String response = switch (request.mode().toLowerCase()) {
            case "react" -> reActAgent.solve(request.message());
            case "multiagent" -> multiAgentOrchestrator.routeRequest(request.message());
            case "collaborative" -> multiAgentOrchestrator.collaborativeRequest(request.message());
            case "decompose" -> {
                TaskDecomposer.TaskExecutionResult result =
                        taskDecomposer.executeComplexTask(request.message());
                yield result.summary();
            }
            default -> "Unknown mode: " + request.mode();
        };

        // Add AI response to memory
        memoryService.addAiMessage(sessionId, response);

        return ResponseEntity.ok(new AgentResponse(response, sessionId, request.mode()));

    } catch (Exception e) {
        log.error("Error executing agent request", e);
        return ResponseEntity.internalServerError()
                .body(new AgentResponse(
                        "An error occurred processing your request",
                        request.sessionId(),
                        request.mode()
                ));
    }
}
```

### Workflow

1. **Extract or Generate Session ID**: If the request includes a session ID, use it. Otherwise, create a new UUID.
2. **Store User Message**: Add the user's input to the session memory.
3. **Execute Agent**: Process the request (ReAct, routing, collaboration, or decomposition).
4. **Store AI Response**: Add the agent's response to the session memory.
5. **Return Session ID**: Send back the session ID so the client can continue the conversation.

## Using Memory in Agents

To make agents context-aware, you can retrieve conversation history and include it in prompts:

```java
public String processWithMemory(String request, String sessionId) {
    // Get conversation history
    List<ChatMessage> history = memoryService.getHistory(sessionId);

    // Build context-aware prompt
    StringBuilder context = new StringBuilder();
    for (ChatMessage msg : history) {
        if (msg instanceof UserMessage) {
            context.append("User: ").append(((UserMessage) msg).text()).append("\n");
        } else if (msg instanceof AiMessage) {
            context.append("Assistant: ").append(((AiMessage) msg).text()).append("\n");
        }
    }

    String prompt = String.format("""
            Previous conversation:
            %s

            Current request: %s

            Provide a response that takes into account the conversation history.
            """, context.toString(), request);

    return chatModel.chat(prompt);
}
```

This allows the agent to reference previous exchanges, making conversations more coherent.

## Memory Best Practices

### 1. Set Reasonable Limits

**Why**: Unlimited memory can:
- Exceed LLM context windows
- Increase costs (more tokens per request)
- Slow down responses

**How**: Use `MessageWindowChatMemory` with `maxMessages=20` or `TokenWindowChatMemory` with a token limit.

### 2. Use TTL

**Why**: Old conversations are rarely needed and waste storage.

**How**: Set a TTL (24-48 hours) so inactive sessions are automatically cleaned up.

### 3. Separate Sessions

**Why**: Mixing different user conversations leads to confusing context.

**How**: Use unique session IDs per user or conversation thread.

### 4. Clear on Logout

**Why**: Respects user privacy and prevents cross-contamination.

**How**: Provide a "clear session" endpoint and call it on logout.

### 5. Monitor Memory Usage

**Why**: Memory leaks can occur if sessions aren't cleaned up.

**How**: Track active session count and memory size in metrics.

## Advanced: Semantic Compression

For long conversations, you can compress history into a summary:

```java
public void compressMemory(String sessionId) {
    List<ChatMessage> history = memoryService.getHistory(sessionId);

    if (history.size() < 10) {
        return; // Not worth compressing
    }

    // Build summary prompt
    String historyText = history.stream()
            .map(msg -> {
                if (msg instanceof UserMessage) {
                    return "User: " + ((UserMessage) msg).text();
                } else if (msg instanceof AiMessage) {
                    return "Assistant: " + ((AiMessage) msg).text();
                }
                return "";
            })
            .collect(Collectors.joining("\n"));

    String summaryPrompt = String.format("""
            Summarize this conversation in 2-3 sentences:

            %s

            Focus on key facts, decisions, and context needed to continue the conversation.
            """, historyText);

    String summary = chatModel.chat(summaryPrompt);

    // Replace history with summary
    memoryService.clearMemory(sessionId);
    memoryService.addMessage(sessionId, "Previous conversation summary: " + summary);
}
```

Call this periodically when history grows too large.

## Testing Memory

```java
@Test
void conversationMemory_RetainsContext() {
    String sessionId = UUID.randomUUID().toString();

    // Turn 1
    memoryService.addMessage(sessionId, "What's the status for customer 12345?");
    memoryService.addAiMessage(sessionId, "Customer 12345 has an Enterprise plan.");

    // Turn 2
    memoryService.addMessage(sessionId, "And their open tickets?");

    List<ChatMessage> history = memoryService.getHistory(sessionId);

    assertThat(history).hasSize(3); // 2 user + 1 AI messages
    assertThat(history.get(0)).isInstanceOf(UserMessage.class);
    assertThat(history.get(1)).isInstanceOf(AiMessage.class);
}

@Test
void clearMemory_RemovesAllMessages() {
    String sessionId = UUID.randomUUID().toString();

    memoryService.addMessage(sessionId, "Hello");
    memoryService.addAiMessage(sessionId, "Hi there!");

    memoryService.clearMemory(sessionId);

    assertThat(memoryService.getMessageCount(sessionId)).isEqualTo(0);
}
```

## Summary

Conversation memory transforms stateless agents into stateful assistants that maintain context across turns.

Key concepts:
- `ChatMemory`: Stores messages for a single conversation
- `ChatMemoryStore`: Persists memories (we use Redis)
- `MessageWindowChatMemory`: Keeps last N messages
- `UserMessage` and `AiMessage`: Represent conversation turns
- Session IDs: Uniquely identify conversations

Best practices:
- Set message limits to prevent unbounded growth
- Use TTL for automatic cleanup
- Separate sessions per user/conversation
- Clear memory on logout
- Consider compression for long conversations

In the next chapter, we'll explore **task decomposition**—breaking complex problems into subtasks with dependency tracking.

---

**Next Chapter**: [06 - Task Decomposition](./06-task-decomposition.md)
