# Workshop Technical Specification
## Architecting Intelligent Enterprise Systems: From Scratch

---

## Overview

This specification provides detailed technical guidance for creating lab instructions for a hands-on AI workshop using Spring Boot, Langchain4J, and Java 25. The workshop follows a unified demo project that evolves through six modules.

---

## Unified Demo Project: **Enterprise Knowledge Assistant**

**Use Case:** Building an intelligent assistant for a fictional company "TechCorp" that helps employees query internal documentation, product manuals, and customer support knowledge bases. The system evolves from basic vector search to a secure, production-ready autonomous agent.

**Dataset:**
- Product documentation (PDF/Markdown)
- Customer support tickets (JSON)
- Internal wiki pages (HTML)
- Technical specifications (Markdown)
- ~50MB of structured and unstructured data

**Evolution Across Modules:**
- **Module 1:** Build embeddings and vector search
- **Module 2:** Add RAG capabilities with hybrid search
- **Module 3:** Connect to external tools (databases, APIs)
- **Module 4:** Transform into an autonomous agent
- **Module 5:** Add security guardrails
- **Module 6:** Deploy to production on OpenShift

---

## Technology Stack

### Core Dependencies

```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.4.0</version>
    </dependency>

    <!-- Langchain4J Core -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>1.11.0</version>
    </dependency>

    <!-- Langchain4J OpenAI Integration -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-open-ai</artifactId>
        <version>1.11.0</version>
    </dependency>

    <!-- Langchain4J Embeddings -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-embeddings-all-minilm-l6-v2</artifactId>
        <version>1.11.0</version>
    </dependency>

    <!-- Vector Store - Chroma -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-chroma</artifactId>
        <version>1.11.0</version>
    </dependency>

    <!-- Document Loaders -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-document-loader-poi</artifactId>
        <version>1.11.0</version>
    </dependency>

    <!-- Redis for caching -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- PostgreSQL for structured data -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
</dependencies>
```

### Additional Tools
- **Docker Compose** for local vector DB (Chroma, PostgreSQL with pgvector)
- **OpenShift CLI** for deployment
- **Prometheus/Grafana** for observability (Module 6)

### Documentation
- **GitBook** for writing and publishing workshop lab instructions

---

## Module 1: The Physics of AI – Vectors & Embeddings

### Learning Objectives
1. Generate embeddings using different models
2. Understand and visualize vector dimensionality
3. Implement distance metric comparisons
4. Build semantic chunking strategies

### Code Examples

#### 1.1 Basic Embedding Generation
**File:** `src/main/java/com/techcorp/assistant/embeddings/EmbeddingService.java`

```java
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService() {
        // Using local all-MiniLM-L6-v2 model (384 dimensions)
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    }

    public Embedding generateEmbedding(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content();
    }

    public float[] getVector(String text) {
        Embedding embedding = generateEmbedding(text);
        return embedding.vector();
    }
}
```

**Lab Exercise:** Students implement embedding generation for sample documents and examine vector dimensions.

---

#### 1.2 Distance Metrics Comparison
**File:** `src/main/java/com/techcorp/assistant/similarity/SimilarityCalculator.java`

```java
@Component
public class SimilarityCalculator {

    public double cosineSimilarity(float[] vectorA, float[] vectorB) {
        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += Math.pow(vectorA[i], 2);
            normB += Math.pow(vectorB[i], 2);
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public double euclideanDistance(float[] vectorA, float[] vectorB) {
        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            sum += Math.pow(vectorA[i] - vectorB[i], 2);
        }
        return Math.sqrt(sum);
    }

    public double dotProduct(float[] vectorA, float[] vectorB) {
        double sum = 0.0;
        for (int i = 0; i < vectorA.length; i++) {
            sum += vectorA[i] * vectorB[i];
        }
        return sum;
    }
}
```

**Lab Exercise:** Compare search results using different distance metrics on the same query.

---

#### 1.3 Semantic vs. Recursive Chunking
**File:** `src/main/java/com/techcorp/assistant/chunking/DocumentChunker.java`

```java
@Service
public class DocumentChunker {

    // Recursive Character Splitter
    public List<TextSegment> recursiveChunk(Document document, int chunkSize, int overlap) {
        DocumentSplitter splitter = DocumentSplitters.recursive(
            chunkSize,
            overlap,
            new OpenAiTokenizer()
        );
        return splitter.split(document);
    }

    // Semantic Chunking (sentence-based)
    public List<TextSegment> semanticChunk(Document document) {
        // Split by paragraphs and sentences while preserving context
        String[] paragraphs = document.text().split("\n\n");
        List<TextSegment> segments = new ArrayList<>();

        for (String paragraph : paragraphs) {
            if (paragraph.length() > 500) {
                // Further split large paragraphs
                segments.addAll(splitBySemanticBoundaries(paragraph));
            } else {
                segments.add(TextSegment.from(paragraph));
            }
        }
        return segments;
    }

    private List<TextSegment> splitBySemanticBoundaries(String text) {
        // Use sentence detection or semantic similarity
        // Implementation details...
        return List.of(TextSegment.from(text));
    }
}
```

**Lab Exercise:** Students chunk a technical document using both strategies and compare retrieval quality.

---

#### 1.4 Vector Store Integration
**File:** `src/main/java/com/techcorp/assistant/store/VectorStoreService.java`

```java
@Service
public class VectorStoreService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public VectorStoreService() {
        // Chroma DB running in Docker
        this.embeddingStore = ChromaEmbeddingStore.builder()
            .baseUrl("http://localhost:8000")
            .collectionName("techcorp-docs")
            .build();
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
    }

    public void ingest(List<Document> documents) {
        DocumentSplitter splitter = DocumentSplitters.recursive(300, 30);

        for (Document doc : documents) {
            List<TextSegment> segments = splitter.split(doc);
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);
        }
    }

    public List<EmbeddingMatch<TextSegment>> search(String query, int maxResults) {
        Embedding queryEmbedding = embeddingModel.embed(query).content();
        return embeddingStore.findRelevant(queryEmbedding, maxResults);
    }
}
```

**Lab Exercise:** Load TechCorp documents into Chroma and perform vector similarity searches.

---

### Demo Application for Module 1
**Endpoint:** `/api/v1/search/vector`

```java
@RestController
@RequestMapping("/api/v1/search")
public class VectorSearchController {

    private final VectorStoreService vectorStore;

    @PostMapping("/vector")
    public ResponseEntity<SearchResponse> vectorSearch(@RequestBody SearchRequest request) {
        List<EmbeddingMatch<TextSegment>> results =
            vectorStore.search(request.getQuery(), request.getMaxResults());

        List<SearchResult> searchResults = results.stream()
            .map(match -> new SearchResult(
                match.embedded().text(),
                match.score(),
                match.embedded().metadata()
            ))
            .collect(Collectors.toList());

        return ResponseEntity.ok(new SearchResponse(searchResults));
    }
}
```

---

## Module 2: Advanced RAG (Retrieval-Augmented Generation)

### Learning Objectives
1. Implement hybrid search (BM25 + Vector)
2. Add cross-encoder re-ranking
3. Implement query transformation techniques
4. Build a complete RAG pipeline

### Code Examples

#### 2.1 Hybrid Search Architecture
**File:** `src/main/java/com/techcorp/assistant/rag/HybridSearchService.java`

```java
@Service
public class HybridSearchService {

    private final VectorStoreService vectorStore;
    private final KeywordSearchService keywordSearch;
    private final ReRanker reRanker;

    public List<TextSegment> hybridSearch(String query, int topK) {
        // Stage 1: Parallel retrieval
        List<TextSegment> vectorResults = vectorStore.search(query, topK * 2)
            .stream()
            .map(EmbeddingMatch::embedded)
            .collect(Collectors.toList());

        List<TextSegment> keywordResults = keywordSearch.search(query, topK * 2);

        // Stage 2: Merge results using Reciprocal Rank Fusion (RRF)
        List<TextSegment> mergedResults = reciprocalRankFusion(
            vectorResults,
            keywordResults,
            topK * 2
        );

        // Stage 3: Re-rank with Cross-Encoder
        return reRanker.rerank(query, mergedResults, topK);
    }

    private List<TextSegment> reciprocalRankFusion(
            List<TextSegment> list1,
            List<TextSegment> list2,
            int k) {
        Map<String, Double> scores = new HashMap<>();
        int rankConstant = 60;

        // Score from list 1
        for (int i = 0; i < list1.size(); i++) {
            String id = list1.get(i).text();
            scores.merge(id, 1.0 / (rankConstant + i + 1), Double::sum);
        }

        // Score from list 2
        for (int i = 0; i < list2.size(); i++) {
            String id = list2.get(i).text();
            scores.merge(id, 1.0 / (rankConstant + i + 1), Double::sum);
        }

        // Sort by combined score
        return scores.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
            .limit(k)
            .map(e -> list1.stream()
                .filter(seg -> seg.text().equals(e.getKey()))
                .findFirst()
                .orElse(list2.stream()
                    .filter(seg -> seg.text().equals(e.getKey()))
                    .findFirst()
                    .orElse(null)))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
}
```

**Lab Exercise:** Implement and compare vector-only vs. hybrid search on technical queries.

---

#### 2.2 Cross-Encoder Re-Ranking
**File:** `src/main/java/com/techcorp/assistant/rag/CrossEncoderReRanker.java`

```java
@Component
public class CrossEncoderReRanker implements ReRanker {

    private final ScoringModel scoringModel;

    public CrossEncoderReRanker() {
        // Using a cross-encoder model for re-ranking
        this.scoringModel = new HuggingFaceScoringModel(
            "cross-encoder/ms-marco-MiniLM-L-6-v2"
        );
    }

    @Override
    public List<TextSegment> rerank(String query, List<TextSegment> segments, int topK) {
        List<ScoredSegment> scored = new ArrayList<>();

        for (TextSegment segment : segments) {
            double score = scoringModel.score(query, segment.text());
            scored.add(new ScoredSegment(segment, score));
        }

        return scored.stream()
            .sorted(Comparator.comparingDouble(ScoredSegment::score).reversed())
            .limit(topK)
            .map(ScoredSegment::segment)
            .collect(Collectors.toList());
    }
}
```

**Lab Exercise:** Measure precision@k improvement with and without re-ranking.

---

#### 2.3 Query Transformation - Multi-Query
**File:** `src/main/java/com/techcorp/assistant/rag/QueryTransformer.java`

```java
@Service
public class QueryTransformer {

    private final ChatLanguageModel llm;

    public QueryTransformer(ChatLanguageModel llm) {
        this.llm = llm;
    }

    // Multi-Query: Generate multiple perspectives of the same query
    public List<String> multiQuery(String originalQuery) {
        String prompt = """
            You are an AI assistant helping to improve search results.
            Given the user query, generate 3 alternative phrasings that capture
            different aspects or perspectives of the same information need.

            Original query: %s

            Return only the 3 alternative queries, one per line.
            """.formatted(originalQuery);

        String response = llm.generate(prompt);
        return Arrays.asList(response.split("\n"))
            .stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }

    // HyDE: Generate a hypothetical document
    public String generateHypotheticalDocument(String query) {
        String prompt = """
            Given the following question, write a detailed paragraph that would
            contain the answer. This is a hypothetical document.

            Question: %s

            Hypothetical Document:
            """.formatted(query);

        return llm.generate(prompt);
    }
}
```

**Lab Exercise:** Implement multi-query retrieval and compare recall rates.

---

#### 2.4 Complete RAG Pipeline
**File:** `src/main/java/com/techcorp/assistant/rag/RAGService.java`

```java
@Service
public class RAGService {

    private final HybridSearchService searchService;
    private final ChatLanguageModel llm;
    private final QueryTransformer queryTransformer;

    public String query(String userQuestion) {
        // Step 1: Query transformation (optional)
        List<String> queries = queryTransformer.multiQuery(userQuestion);
        queries.add(userQuestion); // Include original

        // Step 2: Retrieve from multiple queries
        List<TextSegment> allResults = new ArrayList<>();
        for (String query : queries) {
            allResults.addAll(searchService.hybridSearch(query, 5));
        }

        // Step 3: Deduplicate and take top K
        List<TextSegment> topResults = deduplicateAndRank(allResults, 10);

        // Step 4: Build context
        String context = topResults.stream()
            .map(TextSegment::text)
            .collect(Collectors.joining("\n\n"));

        // Step 5: Generate answer
        String prompt = """
            You are TechCorp's AI assistant. Answer the user's question based
            strictly on the provided context. If the context doesn't contain
            the answer, say "I don't have enough information."

            Context:
            %s

            Question: %s

            Answer:
            """.formatted(context, userQuestion);

        return llm.generate(prompt);
    }

    private List<TextSegment> deduplicateAndRank(List<TextSegment> segments, int topK) {
        return segments.stream()
            .distinct()
            .limit(topK)
            .collect(Collectors.toList());
    }
}
```

**Lab Exercise:** Build end-to-end RAG pipeline and test with complex queries.

---

### Demo Application for Module 2
**Endpoint:** `/api/v1/rag/query`

```java
@RestController
@RequestMapping("/api/v1/rag")
public class RAGController {

    private final RAGService ragService;

    @PostMapping("/query")
    public ResponseEntity<RAGResponse> query(@RequestBody RAGRequest request) {
        String answer = ragService.query(request.getQuestion());
        return ResponseEntity.ok(new RAGResponse(answer));
    }
}
```

---

## Module 3: Tools & The Model Context Protocol (MCP)

### Learning Objectives
1. Understand MCP architecture
2. Build custom tools following MCP standards
3. Integrate with databases and external APIs
4. Implement tool discovery and selection

### Code Examples

#### 3.1 MCP Tool Definition
**File:** `src/main/java/com/techcorp/assistant/tools/CustomerDataTool.java`

```java
@Component
public class CustomerDataTool {

    private final JdbcTemplate jdbcTemplate;

    @Tool("Retrieves customer information by customer ID")
    public String getCustomerInfo(@P("Customer ID") String customerId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT * FROM customers WHERE id = ?",
                (rs, rowNum) -> String.format(
                    "Customer: %s, Email: %s, Plan: %s",
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("subscription_plan")
                ),
                customerId
            );
        } catch (Exception e) {
            return "Customer not found";
        }
    }

    @Tool("Searches customer support tickets by status")
    public List<String> searchTickets(@P("Status: open, closed, pending") String status) {
        return jdbcTemplate.query(
            "SELECT id, subject, created_at FROM tickets WHERE status = ? LIMIT 10",
            (rs, rowNum) -> String.format(
                "Ticket #%s: %s (Created: %s)",
                rs.getString("id"),
                rs.getString("subject"),
                rs.getTimestamp("created_at")
            ),
            status
        );
    }
}
```

**Lab Exercise:** Create a custom tool that queries product inventory database.

---

#### 3.2 MCP Server Configuration
**File:** `src/main/java/com/techcorp/assistant/mcp/MCPServerConfig.java`

```java
@Configuration
public class MCPServerConfig {

    @Bean
    public ChatLanguageModel chatModel(
            @Qualifier("customerDataTool") CustomerDataTool customerTool,
            @Qualifier("weatherTool") WeatherTool weatherTool) {

        return OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-4")
            .temperature(0.7)
            .tools(customerTool, weatherTool)
            .build();
    }
}
```

---

#### 3.3 External API Tool
**File:** `src/main/java/com/techcorp/assistant/tools/WeatherTool.java`

```java
@Component
public class WeatherTool {

    private final RestTemplate restTemplate;

    @Tool("Gets current weather for a given city")
    public String getCurrentWeather(@P("City name") String city) {
        String apiUrl = "https://api.openweathermap.org/data/2.5/weather?q="
            + city + "&appid=" + System.getenv("WEATHER_API_KEY");

        try {
            WeatherResponse response = restTemplate.getForObject(
                apiUrl,
                WeatherResponse.class
            );

            return String.format(
                "Weather in %s: %s, Temperature: %.1f°C",
                city,
                response.getWeather().get(0).getDescription(),
                response.getMain().getTemp() - 273.15
            );
        } catch (Exception e) {
            return "Unable to fetch weather data for " + city;
        }
    }
}
```

**Lab Exercise:** Build a tool that fetches real-time data from a public API.

---

#### 3.4 Tool Orchestration Service
**File:** `src/main/java/com/techcorp/assistant/mcp/ToolOrchestrator.java`

```java
@Service
public class ToolOrchestrator {

    private final ChatLanguageModel llmWithTools;

    public String handleRequest(String userMessage) {
        UserMessage userMsg = UserMessage.from(userMessage);

        Response<AiMessage> response = llmWithTools.generate(userMsg);

        // Check if model wants to use tools
        if (response.content().hasToolExecutionRequests()) {
            List<ToolExecutionRequest> toolRequests =
                response.content().toolExecutionRequests();

            // Execute tools and get results
            List<ToolExecutionResult> toolResults = executingTools(toolRequests);

            // Send tool results back to model
            ToolExecutionResultMessage resultMessage =
                ToolExecutionResultMessage.from(toolResults);

            Response<AiMessage> finalResponse = llmWithTools.generate(
                userMsg,
                response.content(),
                resultMessage
            );

            return finalResponse.content().text();
        }

        return response.content().text();
    }

    private List<ToolExecutionResult> executingTools(
            List<ToolExecutionRequest> requests) {
        // Tools are executed automatically by Langchain4J
        return Collections.emptyList();
    }
}
```

**Lab Exercise:** Test tool selection with ambiguous queries.

---

### Demo Application for Module 3
**Endpoint:** `/api/v1/assistant/chat`

```java
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final ToolOrchestrator orchestrator;

    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String response = orchestrator.handleRequest(request.getMessage());
        return ResponseEntity.ok(new ChatResponse(response));
    }
}
```

---

## Module 4: From Chatbots to Agents

### Learning Objectives
1. Implement ReAct (Reason + Act) pattern
2. Build stateful conversation memory
3. Create multi-agent orchestration
4. Handle task decomposition

### Code Examples

#### 4.1 ReAct Agent Implementation
**File:** `src/main/java/com/techcorp/assistant/agent/ReActAgent.java`

```java
@Service
public class ReActAgent {

    private final ChatLanguageModel llm;
    private final ToolOrchestrator toolOrchestrator;
    private final ConversationMemory memory;

    private static final String REACT_PROMPT = """
        You are an AI agent that follows the ReAct pattern.

        For each user request:
        1. THOUGHT: Analyze what you need to do
        2. ACTION: Choose a tool to use (if needed)
        3. OBSERVATION: Review the tool's result
        4. Repeat until you can provide a final answer

        Available tools: %s

        User request: %s

        Let's solve this step by step:
        """;

    public String solve(String userRequest, List<String> availableTools, int maxIterations) {
        StringBuilder reasoning = new StringBuilder();
        String currentThought = userRequest;

        for (int i = 0; i < maxIterations; i++) {
            // Thought phase
            String prompt = String.format(REACT_PROMPT,
                String.join(", ", availableTools),
                currentThought);

            String thought = llm.generate(prompt);
            reasoning.append("THOUGHT ").append(i + 1).append(": ").append(thought).append("\n");

            // Check if we have a final answer
            if (thought.toLowerCase().contains("final answer:")) {
                return extractFinalAnswer(thought);
            }

            // Action phase - extract tool to use
            String action = extractAction(thought);
            if (action != null) {
                reasoning.append("ACTION ").append(i + 1).append(": ").append(action).append("\n");

                // Execute tool
                String observation = toolOrchestrator.handleRequest(action);
                reasoning.append("OBSERVATION ").append(i + 1).append(": ").append(observation).append("\n");

                currentThought = observation;
            } else {
                break;
            }
        }

        return reasoning.toString();
    }

    private String extractAction(String thought) {
        // Parse thought to extract ACTION: ... pattern
        Pattern pattern = Pattern.compile("ACTION:\\s*(.+?)(?=\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(thought);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private String extractFinalAnswer(String thought) {
        Pattern pattern = Pattern.compile("FINAL ANSWER:\\s*(.+)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(thought);
        return matcher.find() ? matcher.group(1).trim() : thought;
    }
}
```

**Lab Exercise:** Build a ReAct agent that solves multi-step problems.

---

#### 4.2 Stateful Conversation Memory
**File:** `src/main/java/com/techcorp/assistant/agent/ConversationMemoryService.java`

```java
@Service
public class ConversationMemoryService {

    private final Map<String, MessageWindowChatMemory> sessionMemories = new ConcurrentHashMap<>();
    private final RedisTemplate<String, ChatMessage> redisTemplate;

    public ChatMemory getOrCreateMemory(String sessionId, int maxMessages) {
        return sessionMemories.computeIfAbsent(sessionId, id ->
            MessageWindowChatMemory.builder()
                .id(sessionId)
                .maxMessages(maxMessages)
                .chatMemoryStore(new RedisChatMemoryStore(redisTemplate))
                .build()
        );
    }

    public void addMessage(String sessionId, ChatMessage message) {
        ChatMemory memory = getOrCreateMemory(sessionId, 20);
        memory.add(message);
    }

    public List<ChatMessage> getHistory(String sessionId) {
        ChatMemory memory = getOrCreateMemory(sessionId, 20);
        return memory.messages();
    }

    public void clearMemory(String sessionId) {
        sessionMemories.remove(sessionId);
        redisTemplate.delete("chat-memory:" + sessionId);
    }

    // Persistent memory store using Redis
    static class RedisChatMemoryStore implements ChatMemoryStore {
        private final RedisTemplate<String, ChatMessage> redis;

        public RedisChatMemoryStore(RedisTemplate<String, ChatMessage> redis) {
            this.redis = redis;
        }

        @Override
        public List<ChatMessage> getMessages(Object memoryId) {
            String key = "chat-memory:" + memoryId;
            return redis.opsForList().range(key, 0, -1);
        }

        @Override
        public void updateMessages(Object memoryId, List<ChatMessage> messages) {
            String key = "chat-memory:" + memoryId;
            redis.delete(key);
            if (!messages.isEmpty()) {
                redis.opsForList().rightPushAll(key, messages);
                redis.expire(key, Duration.ofHours(24));
            }
        }

        @Override
        public void deleteMessages(Object memoryId) {
            redis.delete("chat-memory:" + memoryId);
        }
    }
}
```

**Lab Exercise:** Implement session-based conversation tracking.

---

#### 4.3 Multi-Agent Orchestration
**File:** `src/main/java/com/techcorp/assistant/agent/MultiAgentOrchestrator.java`

```java
@Service
public class MultiAgentOrchestrator {

    private final Map<String, SpecializedAgent> agents;
    private final ChatLanguageModel coordinatorLLM;

    public MultiAgentOrchestrator(
            CustomerSupportAgent supportAgent,
            TechnicalDocAgent docAgent,
            ProductExpertAgent productAgent) {

        this.agents = Map.of(
            "support", supportAgent,
            "documentation", docAgent,
            "product", productAgent
        );
    }

    public String handleRequest(String userRequest) {
        // Step 1: Coordinator decides which agent(s) to use
        String routingPrompt = """
            You are a routing coordinator. Analyze the user request and decide
            which specialized agent should handle it:
            - support: Customer support issues, tickets, account problems
            - documentation: Technical documentation, how-to guides
            - product: Product features, specifications, comparisons

            Request: %s

            Respond with just the agent name.
            """.formatted(userRequest);

        String selectedAgent = coordinatorLLM.generate(routingPrompt).trim();

        // Step 2: Route to appropriate agent
        SpecializedAgent agent = agents.get(selectedAgent.toLowerCase());
        if (agent == null) {
            agent = agents.get("support"); // Default fallback
        }

        return agent.process(userRequest);
    }

    public String collaborativeRequest(String complexRequest) {
        // For complex requests requiring multiple agents
        List<String> responses = new ArrayList<>();

        // Each agent contributes their perspective
        for (Map.Entry<String, SpecializedAgent> entry : agents.entrySet()) {
            String agentResponse = entry.getValue().process(complexRequest);
            responses.add(entry.getKey() + ": " + agentResponse);
        }

        // Coordinator synthesizes responses
        String synthesisPrompt = """
            Synthesize these agent responses into a comprehensive answer:

            %s

            Provide a unified, coherent response:
            """.formatted(String.join("\n\n", responses));

        return coordinatorLLM.generate(synthesisPrompt);
    }
}

interface SpecializedAgent {
    String process(String request);
}
```

**Lab Exercise:** Build a multi-agent system with routing logic.

---

#### 4.4 Task Decomposition Agent
**File:** `src/main/java/com/techcorp/assistant/agent/TaskDecomposer.java`

```java
@Service
public class TaskDecomposer {

    private final ChatLanguageModel llm;
    private final ToolOrchestrator toolOrchestrator;

    public TaskExecutionResult executeComplexTask(String task) {
        // Step 1: Decompose into subtasks
        List<Subtask> subtasks = decompose(task);

        // Step 2: Execute each subtask
        List<SubtaskResult> results = new ArrayList<>();
        for (Subtask subtask : subtasks) {
            String result = toolOrchestrator.handleRequest(subtask.description());
            results.add(new SubtaskResult(subtask, result));
        }

        // Step 3: Synthesize results
        return synthesizeResults(task, results);
    }

    private List<Subtask> decompose(String complexTask) {
        String prompt = """
            Break down this complex task into 3-5 sequential subtasks.
            Each subtask should be concrete and actionable.

            Task: %s

            Return subtasks in JSON format:
            [
              {"id": 1, "description": "...", "dependencies": []},
              {"id": 2, "description": "...", "dependencies": [1]}
            ]
            """.formatted(complexTask);

        String response = llm.generate(prompt);
        return parseSubtasks(response);
    }

    private List<Subtask> parseSubtasks(String json) {
        // Parse JSON response into Subtask objects
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.readValue(json, new TypeReference<List<Subtask>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    private TaskExecutionResult synthesizeResults(String originalTask, List<SubtaskResult> results) {
        String summary = results.stream()
            .map(r -> r.subtask().description() + ": " + r.result())
            .collect(Collectors.joining("\n"));

        return new TaskExecutionResult(originalTask, results, summary);
    }
}

record Subtask(int id, String description, List<Integer> dependencies) {}
record SubtaskResult(Subtask subtask, String result) {}
record TaskExecutionResult(String task, List<SubtaskResult> results, String summary) {}
```

**Lab Exercise:** Implement task decomposition for complex queries.

---

### Demo Application for Module 4
**Endpoint:** `/api/v1/agent/execute`

```java
@RestController
@RequestMapping("/api/v1/agent")
public class AgentController {

    private final ReActAgent reactAgent;
    private final MultiAgentOrchestrator orchestrator;
    private final ConversationMemoryService memoryService;

    @PostMapping("/execute")
    public ResponseEntity<AgentResponse> execute(@RequestBody AgentRequest request) {
        String sessionId = request.getSessionId();
        ChatMemory memory = memoryService.getOrCreateMemory(sessionId, 20);

        // Add user message to memory
        memoryService.addMessage(sessionId, UserMessage.from(request.getMessage()));

        // Execute agent
        String response = orchestrator.handleRequest(request.getMessage());

        // Add AI response to memory
        memoryService.addMessage(sessionId, AiMessage.from(response));

        return ResponseEntity.ok(new AgentResponse(response, sessionId));
    }
}
```

---

## Module 5: Security & Guardrails

### Learning Objectives
1. Defend against prompt injection attacks
2. Implement PII masking and data protection
3. Add output validation and content filtering
4. Create security monitoring

### Code Examples

#### 5.1 Prompt Injection Defense
**File:** `src/main/java/com/techcorp/assistant/security/PromptInjectionGuard.java`

```java
@Component
public class PromptInjectionGuard {

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        Pattern.compile("ignore (previous|above|all) (instructions|prompts)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("system:.*override", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[INST\\].*\\[/INST\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("you are now", Pattern.CASE_INSENSITIVE)
    );

    public ValidationResult validate(String userInput) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            Matcher matcher = pattern.matcher(userInput);
            if (matcher.find()) {
                return ValidationResult.rejected(
                    "Potential prompt injection detected: " + matcher.group()
                );
            }
        }

        // Check for excessive special characters
        long specialCharCount = userInput.chars()
            .filter(ch -> !Character.isLetterOrDigit(ch) && !Character.isWhitespace(ch))
            .count();

        if (specialCharCount > userInput.length() * 0.3) {
            return ValidationResult.rejected("Excessive special characters detected");
        }

        return ValidationResult.approved();
    }

    public String sanitizeInput(String input) {
        // Remove XML/HTML tags
        String cleaned = input.replaceAll("<[^>]*>", "");

        // Normalize whitespace
        cleaned = cleaned.replaceAll("\\s+", " ").trim();

        return cleaned;
    }
}

record ValidationResult(boolean approved, String reason) {
    static ValidationResult approved() {
        return new ValidationResult(true, null);
    }

    static ValidationResult rejected(String reason) {
        return new ValidationResult(false, reason);
    }
}
```

**Lab Exercise:** Test various prompt injection techniques and defenses.

---

#### 5.2 PII Masking Service
**File:** `src/main/java/com/techcorp/assistant/security/PIIMaskingService.java`

```java
@Service
public class PIIMaskingService {

    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");

    private static final Pattern PHONE_PATTERN =
        Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");

    private static final Pattern SSN_PATTERN =
        Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");

    private static final Pattern CREDIT_CARD_PATTERN =
        Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");

    public String maskPII(String text) {
        String masked = text;

        // Mask emails
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL_REDACTED]");

        // Mask phone numbers
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE_REDACTED]");

        // Mask SSN
        masked = SSN_PATTERN.matcher(masked).replaceAll("[SSN_REDACTED]");

        // Mask credit cards
        masked = CREDIT_CARD_PATTERN.matcher(masked).replaceAll("[CARD_REDACTED]");

        return masked;
    }

    public PIIDetectionResult detectPII(String text) {
        List<PIIMatch> matches = new ArrayList<>();

        Matcher emailMatcher = EMAIL_PATTERN.matcher(text);
        while (emailMatcher.find()) {
            matches.add(new PIIMatch("EMAIL", emailMatcher.group(), emailMatcher.start()));
        }

        Matcher phoneMatcher = PHONE_PATTERN.matcher(text);
        while (phoneMatcher.find()) {
            matches.add(new PIIMatch("PHONE", phoneMatcher.group(), phoneMatcher.start()));
        }

        return new PIIDetectionResult(!matches.isEmpty(), matches);
    }
}

record PIIMatch(String type, String value, int position) {}
record PIIDetectionResult(boolean containsPII, List<PIIMatch> matches) {}
```

**Lab Exercise:** Implement PII detection in RAG retrieval pipeline.

---

#### 5.3 Output Validation with LLM Judge
**File:** `src/main/java/com/techcorp/assistant/security/OutputValidator.java`

```java
@Service
public class OutputValidator {

    private final ChatLanguageModel judgeModel;

    public OutputValidator() {
        // Using a smaller, faster model for validation
        this.judgeModel = OpenAiChatModel.builder()
            .apiKey(System.getenv("OPENAI_API_KEY"))
            .modelName("gpt-3.5-turbo")
            .temperature(0.0)
            .build();
    }

    public ValidationResult validateOutput(String output, ValidationCriteria criteria) {
        String prompt = """
            You are a content safety validator. Analyze the following AI response
            and determine if it violates any of these criteria:

            Criteria:
            - No toxic or harmful content
            - No disclosure of confidential information
            - Professional and respectful tone
            - Factually grounded (no hallucinations about company data)

            AI Response:
            %s

            Respond in JSON:
            {
              "safe": true/false,
              "violations": ["list of violations, if any"],
              "confidence": 0.0-1.0
            }
            """.formatted(output);

        String judgeResponse = judgeModel.generate(prompt);
        return parseValidationResult(judgeResponse);
    }

    public boolean containsHallucination(String output, List<String> sourceDocuments) {
        String prompt = """
            Compare the AI response with the source documents.
            Determine if the response contains information NOT present in the sources.

            Sources:
            %s

            AI Response:
            %s

            Does the response contain hallucinated information? (yes/no)
            """.formatted(String.join("\n\n", sourceDocuments), output);

        String response = judgeModel.generate(prompt).toLowerCase();
        return response.contains("yes");
    }

    private ValidationResult parseValidationResult(String json) {
        // Parse JSON response
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(json, ValidationResult.class);
        } catch (JsonProcessingException e) {
            return ValidationResult.rejected("Unable to validate output");
        }
    }
}

record ValidationCriteria(boolean checkToxicity, boolean checkConfidentiality, boolean checkFactuality) {}
```

**Lab Exercise:** Implement output filtering for toxic content.

---

#### 5.4 Access Control for RAG
**File:** `src/main/java/com/techcorp/assistant/security/DocumentAccessControl.java`

```java
@Service
public class DocumentAccessControl {

    private final UserRepository userRepository;

    public List<TextSegment> filterByPermissions(
            List<TextSegment> documents,
            String userId) {

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new UnauthorizedException("User not found"));

        return documents.stream()
            .filter(doc -> hasAccess(user, doc))
            .collect(Collectors.toList());
    }

    private boolean hasAccess(User user, TextSegment document) {
        // Check document metadata for access control
        Map<String, String> metadata = document.metadata().toMap();
        String requiredRole = metadata.get("required_role");
        String department = metadata.get("department");

        if (requiredRole != null && !user.hasRole(requiredRole)) {
            return false;
        }

        if (department != null && !user.getDepartment().equals(department)) {
            return false;
        }

        return true;
    }

    public void enrichWithACL(TextSegment segment, String role, String department) {
        segment.metadata().put("required_role", role);
        segment.metadata().put("department", department);
    }
}
```

**Lab Exercise:** Implement role-based access control for document retrieval.

---

#### 5.5 Security Monitoring and Logging
**File:** `src/main/java/com/techcorp/assistant/security/SecurityAuditService.java`

```java
@Service
public class SecurityAuditService {

    private static final Logger logger = LoggerFactory.getLogger(SecurityAuditService.class);

    private final RedisTemplate<String, AuditEvent> auditStore;

    public void logSecurityEvent(SecurityEvent event) {
        AuditEvent auditEvent = new AuditEvent(
            UUID.randomUUID().toString(),
            event.type(),
            event.severity(),
            event.userId(),
            event.details(),
            Instant.now()
        );

        // Log to application logs
        logger.warn("Security Event: {} - User: {} - Details: {}",
            event.type(), event.userId(), event.details());

        // Store in Redis for real-time monitoring
        auditStore.opsForList().rightPush("security-events", auditEvent);

        // Alert if high severity
        if (event.severity() == Severity.HIGH || event.severity() == Severity.CRITICAL) {
            sendAlert(auditEvent);
        }
    }

    private void sendAlert(AuditEvent event) {
        // Integration with monitoring system (e.g., Slack, PagerDuty)
        logger.error("CRITICAL SECURITY EVENT: {}", event);
    }

    public List<AuditEvent> getRecentEvents(int limit) {
        return auditStore.opsForList().range("security-events", -limit, -1);
    }
}

record SecurityEvent(String type, Severity severity, String userId, String details) {}
record AuditEvent(String id, String type, Severity severity, String userId, String details, Instant timestamp) {}

enum Severity {
    LOW, MEDIUM, HIGH, CRITICAL
}
```

**Lab Exercise:** Implement security event monitoring dashboard.

---

### Demo Application for Module 5
**Endpoint:** `/api/v1/secure/query`

```java
@RestController
@RequestMapping("/api/v1/secure")
public class SecureRAGController {

    private final PromptInjectionGuard injectionGuard;
    private final PIIMaskingService piiService;
    private final OutputValidator outputValidator;
    private final DocumentAccessControl accessControl;
    private final RAGService ragService;
    private final SecurityAuditService auditService;

    @PostMapping("/query")
    public ResponseEntity<SecureResponse> secureQuery(
            @RequestBody SecureRequest request,
            @AuthenticationPrincipal User user) {

        // Step 1: Validate input for injection
        ValidationResult inputValidation = injectionGuard.validate(request.getQuery());
        if (!inputValidation.approved()) {
            auditService.logSecurityEvent(new SecurityEvent(
                "PROMPT_INJECTION_ATTEMPT",
                Severity.HIGH,
                user.getId(),
                inputValidation.reason()
            ));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new SecureResponse("Invalid input detected"));
        }

        // Step 2: Sanitize input
        String sanitized = injectionGuard.sanitizeInput(request.getQuery());

        // Step 3: Mask PII in input
        String masked = piiService.maskPII(sanitized);

        // Step 4: Execute RAG with access control
        String rawResponse = ragService.query(masked);

        // Step 5: Validate output
        ValidationResult outputValidation = outputValidator.validateOutput(
            rawResponse,
            new ValidationCriteria(true, true, true)
        );

        if (!outputValidation.approved()) {
            auditService.logSecurityEvent(new SecurityEvent(
                "UNSAFE_OUTPUT_BLOCKED",
                Severity.MEDIUM,
                user.getId(),
                outputValidation.reason()
            ));
            return ResponseEntity.ok(new SecureResponse(
                "I cannot provide that information due to safety policies."
            ));
        }

        // Step 6: Mask PII in output
        String finalResponse = piiService.maskPII(rawResponse);

        return ResponseEntity.ok(new SecureResponse(finalResponse));
    }
}
```

---

## Module 6: Enterprise Best Practices & Production

### Learning Objectives
1. Create evaluation benchmarks (Evals)
2. Implement distributed tracing and observability
3. Optimize for performance and cost
4. Deploy to OpenShift

### Code Examples

#### 6.1 LLM Evaluation Framework
**File:** `src/main/java/com/techcorp/assistant/eval/EvaluationService.java`

```java
@Service
public class EvaluationService {

    private final RAGService ragService;
    private final ObjectMapper objectMapper;

    public EvaluationReport runEvaluation(String evalSetPath) throws IOException {
        // Load golden dataset
        List<EvalCase> evalCases = loadEvalSet(evalSetPath);

        List<EvalResult> results = new ArrayList<>();

        for (EvalCase evalCase : evalCases) {
            String actualResponse = ragService.query(evalCase.query());

            // Evaluate response
            double accuracy = calculateAccuracy(evalCase.expectedAnswer(), actualResponse);
            double relevance = calculateRelevance(evalCase.context(), actualResponse);
            double faithfulness = calculateFaithfulness(evalCase.context(), actualResponse);

            results.add(new EvalResult(
                evalCase.id(),
                evalCase.query(),
                actualResponse,
                accuracy,
                relevance,
                faithfulness
            ));
        }

        return new EvaluationReport(results, calculateAverages(results));
    }

    private double calculateAccuracy(String expected, String actual) {
        // Using semantic similarity
        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
        Embedding expectedEmb = model.embed(expected).content();
        Embedding actualEmb = model.embed(actual).content();

        return CosineSimilarity.between(expectedEmb, actualEmb);
    }

    private double calculateRelevance(List<String> contexts, String response) {
        // Check if response is grounded in provided context
        String combinedContext = String.join(" ", contexts);

        EmbeddingModel model = new AllMiniLmL6V2EmbeddingModel();
        Embedding contextEmb = model.embed(combinedContext).content();
        Embedding responseEmb = model.embed(response).content();

        return CosineSimilarity.between(contextEmb, responseEmb);
    }

    private double calculateFaithfulness(List<String> contexts, String response) {
        // Using LLM as a judge
        ChatLanguageModel judge = OpenAiChatModel.builder()
            .modelName("gpt-3.5-turbo")
            .temperature(0.0)
            .build();

        String prompt = """
            Rate the faithfulness of the response to the provided context on a scale of 0-1.
            A faithful response only uses information from the context.

            Context: %s

            Response: %s

            Faithfulness score (0.0-1.0):
            """.formatted(String.join("\n", contexts), response);

        String score = judge.generate(prompt);
        try {
            return Double.parseDouble(score.trim());
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }

    private List<EvalCase> loadEvalSet(String path) throws IOException {
        return objectMapper.readValue(
            new File(path),
            new TypeReference<List<EvalCase>>() {}
        );
    }

    private MetricAverages calculateAverages(List<EvalResult> results) {
        double avgAccuracy = results.stream()
            .mapToDouble(EvalResult::accuracy)
            .average()
            .orElse(0.0);

        double avgRelevance = results.stream()
            .mapToDouble(EvalResult::relevance)
            .average()
            .orElse(0.0);

        double avgFaithfulness = results.stream()
            .mapToDouble(EvalResult::faithfulness)
            .average()
            .orElse(0.0);

        return new MetricAverages(avgAccuracy, avgRelevance, avgFaithfulness);
    }
}

record EvalCase(String id, String query, List<String> context, String expectedAnswer) {}
record EvalResult(String id, String query, String actualResponse, double accuracy, double relevance, double faithfulness) {}
record MetricAverages(double accuracy, double relevance, double faithfulness) {}
record EvaluationReport(List<EvalResult> results, MetricAverages averages) {}
```

**Lab Exercise:** Create a golden evaluation dataset and run benchmarks.

---

#### 6.2 Distributed Tracing
**File:** `src/main/java/com/techcorp/assistant/observability/TracingConfig.java`

```java
@Configuration
public class TracingConfig {

    @Bean
    public Tracer tracer() {
        // OpenTelemetry configuration
        return GlobalOpenTelemetry.getTracer("techcorp-assistant");
    }
}

@Aspect
@Component
public class RAGTracingAspect {

    private final Tracer tracer;

    @Around("@annotation(Traced)")
    public Object traceMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        Span span = tracer.spanBuilder(methodName).startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Add attributes
            span.setAttribute("component", "rag-service");

            Object result = joinPoint.proceed();

            span.setStatus(StatusCode.OK);
            return result;
        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**File:** `src/main/java/com/techcorp/assistant/observability/MetricsCollector.java`

```java
@Service
public class MetricsCollector {

    private final MeterRegistry registry;
    private final Counter queryCounter;
    private final Timer responseTimer;
    private final Gauge tokenGauge;

    public MetricsCollector(MeterRegistry registry) {
        this.registry = registry;

        this.queryCounter = Counter.builder("rag.queries.total")
            .description("Total number of RAG queries")
            .register(registry);

        this.responseTimer = Timer.builder("rag.response.time")
            .description("RAG response time")
            .register(registry);

        this.tokenGauge = Gauge.builder("rag.tokens.used", this, MetricsCollector::getTokensUsed)
            .description("Total tokens used")
            .register(registry);
    }

    public void recordQuery() {
        queryCounter.increment();
    }

    public void recordResponseTime(long milliseconds) {
        responseTimer.record(milliseconds, TimeUnit.MILLISECONDS);
    }

    public double getTokensUsed() {
        // Fetch from token tracking service
        return 0.0;
    }
}
```

**Lab Exercise:** Implement distributed tracing across the RAG pipeline.

---

#### 6.3 Caching and Optimization
**File:** `src/main/java/com/techcorp/assistant/optimization/CachingService.java`

```java
@Service
public class CachingService {

    private final RedisTemplate<String, String> redisTemplate;
    private final EmbeddingModel embeddingModel;
    private final Map<String, Embedding> embeddingCache = new ConcurrentHashMap<>();

    @Cacheable(value = "rag-responses", key = "#query")
    public String getCachedResponse(String query) {
        return null; // Cache miss, will be populated
    }

    @CachePut(value = "rag-responses", key = "#query")
    public String cacheResponse(String query, String response) {
        return response;
    }

    // Semantic caching: cache by embedding similarity
    public String semanticCacheGet(String query, double threshold) {
        Embedding queryEmbedding = getOrComputeEmbedding(query);

        // Search cache for similar queries
        Set<String> cachedQueries = redisTemplate.keys("semantic-cache:*");

        for (String key : cachedQueries) {
            String cachedQuery = key.replace("semantic-cache:", "");
            Embedding cachedEmbedding = getOrComputeEmbedding(cachedQuery);

            double similarity = CosineSimilarity.between(queryEmbedding, cachedEmbedding);

            if (similarity > threshold) {
                return redisTemplate.opsForValue().get(key);
            }
        }

        return null;
    }

    public void semanticCachePut(String query, String response) {
        String key = "semantic-cache:" + query;
        redisTemplate.opsForValue().set(key, response, Duration.ofHours(24));
    }

    private Embedding getOrComputeEmbedding(String text) {
        return embeddingCache.computeIfAbsent(text,
            t -> embeddingModel.embed(t).content());
    }
}
```

**Lab Exercise:** Implement semantic caching to reduce LLM calls.

---

#### 6.4 Token Optimization
**File:** `src/main/java/com/techcorp/assistant/optimization/TokenOptimizer.java`

```java
@Service
public class TokenOptimizer {

    private final Tokenizer tokenizer;

    public TokenOptimizer() {
        this.tokenizer = new OpenAiTokenizer();
    }

    public String optimizeContext(List<TextSegment> segments, int maxTokens) {
        List<TokenizedSegment> tokenized = segments.stream()
            .map(seg -> new TokenizedSegment(
                seg,
                tokenizer.estimateTokenCountInText(seg.text())
            ))
            .sorted(Comparator.comparingInt(TokenizedSegment::relevanceScore).reversed())
            .collect(Collectors.toList());

        StringBuilder context = new StringBuilder();
        int currentTokens = 0;

        for (TokenizedSegment ts : tokenized) {
            if (currentTokens + ts.tokens() > maxTokens) {
                break;
            }
            context.append(ts.segment().text()).append("\n\n");
            currentTokens += ts.tokens();
        }

        return context.toString();
    }

    public String compressPrompt(String prompt) {
        // Remove redundant whitespace
        String compressed = prompt.replaceAll("\\s+", " ").trim();

        // Remove filler words for system prompts
        compressed = compressed.replaceAll("\\b(please|kindly|just)\\b", "");

        return compressed;
    }
}

record TokenizedSegment(TextSegment segment, int tokens) {
    int relevanceScore() {
        // Calculate based on metadata, recency, etc.
        return 100;
    }
}
```

**Lab Exercise:** Optimize token usage while maintaining quality.

---

#### 6.5 OpenShift Deployment Configuration

**File:** `deployment/deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: techcorp-assistant
  labels:
    app: techcorp-assistant
spec:
  replicas: 3
  selector:
    matchLabels:
      app: techcorp-assistant
  template:
    metadata:
      labels:
        app: techcorp-assistant
    spec:
      containers:
      - name: assistant
        image: quay.io/techcorp/assistant:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
        - name: OPENAI_API_KEY
          valueFrom:
            secretKeyRef:
              name: openai-secret
              key: api-key
        - name: REDIS_HOST
          value: "redis-service"
        - name: POSTGRES_HOST
          value: "postgres-service"
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: techcorp-assistant-service
spec:
  selector:
    app: techcorp-assistant
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: route.openshift.io/v1
kind: Route
metadata:
  name: techcorp-assistant-route
spec:
  to:
    kind: Service
    name: techcorp-assistant-service
  port:
    targetPort: 8080
  tls:
    termination: edge
```

**File:** `deployment/Dockerfile`

```dockerfile
FROM registry.access.redhat.com/ubi9/openjdk-21:latest

# Copy application JAR
COPY target/techcorp-assistant-*.jar /deployments/app.jar

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "/deployments/app.jar"]
```

**Lab Exercise:** Deploy the application to OpenShift with monitoring.

---

## Lab Instructions Structure (for Technical Writers)

### For Each Module:

#### 1. Introduction Section
- **Learning outcomes** (bullet list)
- **Prerequisites** (previous modules, tools)
- **Estimated time** (minutes)
- **What you'll build** (tangible outcome)

#### 2. Background/Theory Section (5-10 minutes)
- Conceptual explanation with diagrams
- Real-world use cases
- Trade-offs and considerations

#### 3. Hands-On Labs (30-45 minutes per module)

**Lab Structure:**
```
## Lab X.Y: [Title]

### Objective
[What students will accomplish]

### Steps

1. **Setup**
   - Clone starter code
   - Review existing files
   - Start required services (Docker Compose)

2. **Implementation**
   - Step-by-step code writing with explanations
   - Code snippets to add
   - File locations

3. **Testing**
   - How to run the code
   - Expected output
   - Test cases to verify

4. **Verification**
   - Checkpoint questions
   - Expected behavior
   - Troubleshooting tips

### Challenge Exercise (Optional)
[Advanced task to extend the lab]

### Recap
[What was learned]
```

#### 4. Demo Application Section
- Complete working example
- REST API endpoint to test
- Sample requests/responses
- Visual output (screenshots/logs)

#### 5. Knowledge Check
- Multiple choice questions
- Practical exercises
- Discussion prompts

---

## Sample Dataset Structure

### File: `data/eval-golden-set.json`
```json
[
  {
    "id": "eval-001",
    "query": "What is the warranty period for the X500 product?",
    "context": [
      "The X500 comes with a 2-year manufacturer warranty covering defects.",
      "Extended warranty options are available for purchase."
    ],
    "expectedAnswer": "The X500 product has a 2-year manufacturer warranty that covers defects in materials and workmanship."
  },
  {
    "id": "eval-002",
    "query": "How do I reset my password?",
    "context": [
      "To reset your password, click 'Forgot Password' on the login page.",
      "Enter your email address and follow the instructions sent to your inbox."
    ],
    "expectedAnswer": "Click 'Forgot Password' on the login page, enter your email, and follow the reset instructions sent to you."
  }
]
```

---

## Docker Compose for Development

**File:** `docker-compose.yml`

```yaml
version: '3.8'

services:
  chroma:
    image: chromadb/chroma:latest
    ports:
      - "8000:8000"
    volumes:
      - chroma-data:/chroma/chroma
    environment:
      - ALLOW_RESET=true

  postgres:
    image: pgvector/pgvector:pg16
    ports:
      - "5432:5432"
    environment:
      - POSTGRES_PASSWORD=password
      - POSTGRES_DB=techcorp
    volumes:
      - postgres-data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis-data:/data

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./monitoring/prometheus.yml:/etc/prometheus/prometheus.yml

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana-data:/var/lib/grafana

volumes:
  chroma-data:
  postgres-data:
  redis-data:
  grafana-data:
```

---

## Module Progression Summary

| Module | Demo Feature | Key Libraries | API Endpoint |
|--------|-------------|---------------|--------------|
| 1 | Vector search | Langchain4J Embeddings, Chroma | `/api/v1/search/vector` |
| 2 | RAG with hybrid search | Langchain4J, BM25 | `/api/v1/rag/query` |
| 3 | Tool-augmented assistant | MCP, JdbcTemplate | `/api/v1/assistant/chat` |
| 4 | Autonomous agent | ReAct, Redis Memory | `/api/v1/agent/execute` |
| 5 | Secure RAG | Security filters, PII masking | `/api/v1/secure/query` |
| 6 | Production deployment | Micrometer, OpenTelemetry | Deploy to OpenShift |

---

## Additional Resources for Technical Writers

### Code Repository Structure
```
techcorp-assistant/
├── src/
│   ├── main/
│   │   ├── java/com/techcorp/assistant/
│   │   │   ├── embeddings/
│   │   │   ├── rag/
│   │   │   ├── agent/
│   │   │   ├── security/
│   │   │   └── optimization/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── data/
│   └── test/
├── deployment/
│   ├── Dockerfile
│   ├── deployment.yaml
│   └── docker-compose.yml
├── docs/
│   └── modules/ (GitBook structure)
└── pom.xml
```

### Testing Strategy
- **Unit tests:** JUnit 5 for individual components
- **Integration tests:** Testcontainers for database/Redis
- **End-to-end tests:** RestAssured for API testing
- **Performance tests:** JMeter scripts for load testing

---

## Success Criteria

By the end of the workshop, students will have:

1. **Module 1:** Working vector search with 3 distance metrics
2. **Module 2:** Production-ready RAG pipeline with >80% relevance
3. **Module 3:** MCP-compliant tools connecting to 2+ data sources
4. **Module 4:** Autonomous agent solving multi-step tasks
5. **Module 5:** Security-hardened system with PII masking
6. **Module 6:** Application deployed to OpenShift with observability

---

## Appendix: Common Troubleshooting

### Docker Issues
- Chroma connection refused → Check port 8000 is not in use
- PostgreSQL auth failure → Verify password in env vars

### LangChain4J Issues
- Embedding dimension mismatch → Ensure consistent model
- Tool not found → Check @Tool annotation and bean registration

### OpenShift Deployment
- ImagePullBackOff → Verify image registry credentials
- CrashLoopBackOff → Check application logs for startup errors

---

**End of Specification Document**
