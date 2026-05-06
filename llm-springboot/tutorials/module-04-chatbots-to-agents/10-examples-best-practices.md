# Real-World Examples and Best Practices

## Introduction

In this final chapter, we'll explore real-world scenarios, advanced patterns, and best practices for building production-ready agent systems. You'll see complete examples that demonstrate how to combine the concepts from previous chapters.

## Example 1: Customer Support Automation

### Scenario

A customer support system that:
1. Identifies the customer from their query
2. Checks their account status
3. Searches for related support tickets
4. Provides personalized assistance

### Implementation

```java
@Service
public class CustomerSupportAutomation {
    private final ReActAgent reActAgent;
    private final ConversationMemoryService memoryService;

    public String handleCustomerQuery(String query, String sessionId) {
        // Use ReAct pattern for multi-step reasoning
        String response = reActAgent.solve(query);

        // Save to memory for follow-up questions
        memoryService.addMessage(sessionId, query);
        memoryService.addAiMessage(sessionId, response);

        return response;
    }
}
```

### Example Interaction

**User**: "I can't log in and I'm customer 12345"

**Agent Reasoning**:
1. **THOUGHT**: "I need to check if this customer exists and get their account info"
2. **ACTION**: `getCustomerInfo(12345)`
3. **OBSERVATION**: "Customer 12345: John Doe, Enterprise plan, member since 2023"
4. **THOUGHT**: "Customer exists. Let me check if there are any open tickets about login issues"
5. **ACTION**: `searchTickets(open)`
6. **OBSERVATION**: "Found 3 open tickets, including Ticket #101 for customer 12345 about login issues"
7. **FINAL ANSWER**: "Hello John, I see you're on our Enterprise plan. I found that you have an existing ticket (#101) about login issues that our team is currently working on. The expected resolution is within 24 hours. Is there anything specific about the login problem I can help you with right now?"

**Follow-up User**: "When was that ticket created?"

**Agent Response** (with memory): "Ticket #101 for the login issue was created yesterday. Our support team is actively investigating."

### Best Practices

**Context Awareness**: The agent remembers "that ticket" refers to #101 from the previous turn.

**Proactive Information**: Provides expected resolution time without being asked.

**Personalization**: Uses the customer's name and references their plan level.

## Example 2: Technical Documentation Assistant

### Scenario

A documentation assistant that helps developers find configuration examples and API references.

### Implementation

```java
@Service
public class TechnicalDocumentationAssistant {
    private final MultiAgentOrchestrator orchestrator;
    private final VectorStoreService vectorStore; // From Module 01

    public String answerTechnicalQuestion(String question) {
        // First, search documentation using semantic search
        List<String> relevantDocs = vectorStore.search(question, 5);

        // Build context-aware prompt
        String enhancedQuestion = String.format("""
            Question: %s

            Relevant documentation:
            %s

            Please provide a detailed answer with code examples.
            """, question, String.join("\n\n", relevantDocs));

        // Route to technical documentation agent
        return orchestrator.routeRequest(enhancedQuestion);
    }
}
```

### Example Interaction

**User**: "How do I configure JWT authentication?"

**System Flow**:
1. Vector search finds relevant docs about JWT and authentication
2. Orchestrator routes to `TechnicalDocAgent`
3. Agent uses retrieved docs to provide context-aware answer

**Agent Response**:
```
To configure JWT authentication:

1. Add the dependency:
```xml
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt</artifactId>
</dependency>
```

2. Configure in application.properties:
```properties
jwt.secret=your-secret-key
jwt.expiration=3600000
```

3. Create a JwtTokenProvider:
```java
@Component
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(String username) {
        return Jwts.builder()
                .setSubject(username)
                .setIssuedAt(new Date())
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }
}
```

See the full documentation at /docs/authentication/jwt
```

### Best Practices

**RAG Pattern**: Combines retrieval (vector search) with generation (LLM response).

**Code Examples**: Includes practical, copy-pasteable code snippets.

**References**: Links to full documentation for deeper dives.

## Example 3: Multi-Step Research Task

### Scenario

Analyzing customer satisfaction by researching top customers, their tickets, and identifying patterns.

### Implementation

```java
@Service
public class CustomerSatisfactionAnalyzer {
    private final TaskDecomposer taskDecomposer;

    public TaskExecutionResult analyzeCustomerSatisfaction() {
        String complexTask = """
            Analyze customer satisfaction for Enterprise customers by:
            1. Identifying all Enterprise plan customers
            2. Calculating average ticket resolution time for each
            3. Identifying common issues
            4. Providing recommendations to improve satisfaction
            """;

        return taskDecomposer.executeComplexTask(complexTask);
    }
}
```

### Execution Flow

**Task Decomposition**:
```
task1: Query database to identify all Enterprise plan customers
task2 (depends on task1): For each Enterprise customer, calculate average ticket resolution time
task3 (depends on task2): Analyze resolution times and identify customers at risk
task4 (depends on task2): Extract common issues from Enterprise customer tickets
task5 (depends on task3, task4): Synthesize findings and provide actionable recommendations
```

**Execution**:

*Task 1*: Identifies 15 Enterprise customers

*Task 2*: Calculates avg resolution time per customer (ranges from 8 to 48 hours)

*Task 3*: Flags 3 customers with resolution times > 24 hours as "at risk"

*Task 4*: Common issues: authentication (40%), performance (30%), integration (20%), billing (10%)

*Task 5*: Recommendations:
- Prioritize authentication improvements (affects 40% of issues)
- Assign dedicated support engineer to at-risk accounts
- Create self-service documentation for top 3 issues

### Best Practices

**Dependency Tracking**: Tasks 3 and 4 depend on task 2's results.

**Parallel Execution**: Tasks 3 and 4 can run in parallel (no shared dependencies).

**Actionable Output**: Final synthesis provides specific recommendations, not just analysis.

## Example 4: Collaborative Decision-Making

### Scenario

A complex question requiring input from multiple specialists: "Should we offer a free trial for Enterprise features?"

### Implementation

```java
@Service
public class ProductDecisionAssistant {
    private final MultiAgentOrchestrator orchestrator;

    public String evaluateProductDecision(String proposal) {
        // Get perspectives from all specialists
        return orchestrator.collaborativeRequest(proposal);
    }
}
```

### Example Output

**Proposal**: "Should we offer a 14-day free trial for Enterprise features?"

**Customer Support Perspective**:
"From support's view, free trials increase inquiry volume by ~30%, especially during trial expiration. We'd need to scale support capacity. However, trials reduce post-purchase complaints since users know what they're getting."

**Product Expert Perspective**:
"Free trials typically increase conversion rates by 15-20% for Enterprise plans. Key success factors: feature limits (e.g., 10 users max during trial), clear upgrade path, and automated reminder emails. Risk: Users may exploit trials instead of purchasing."

**Technical Documentation Perspective**:
"We'll need comprehensive setup guides, video tutorials, and a trial-to-production migration guide. Estimated 40 hours of documentation work. Also need trial-specific FAQ covering common questions."

**Synthesized Recommendation**:
```
Recommendation: Implement a 14-day Enterprise feature trial with guardrails.

Benefits:
- 15-20% potential conversion rate increase
- Reduced post-purchase complaints
- Better qualified leads

Requirements:
- Scale support capacity by 30%
- 40 hours of documentation work
- Trial limits: 10 users, limited API calls
- Automated trial expiration flow

Implementation plan:
1. Create trial-specific documentation (week 1-2)
2. Hire/train additional support staff (week 2-4)
3. Implement trial infrastructure (week 3-5)
4. Launch with 100 beta testers (week 6)
5. Full rollout (week 8)

ROI: Expected 15% conversion lift on 500 monthly Enterprise inquiries = 75 new customers/month
```

### Best Practices

**Diverse Perspectives**: Each agent contributes unique insights from their domain.

**Synthesis Quality**: The LLM combines perspectives into a coherent recommendation.

**Actionable Output**: Includes implementation plan and ROI estimate.

## Best Practices Summary

### 1. Prompt Engineering

**Be Specific**:
```java
// Good
"You are a technical documentation expert specializing in Spring Boot configuration"

// Bad
"You are a helpful assistant"
```

**Provide Context**:
```java
String prompt = String.format("""
    Previous conversation:
    %s

    Current question: %s

    Answer based on the conversation history.
    """, conversationHistory, currentQuestion);
```

**Use Examples** (Few-shot learning):
```java
String prompt = """
    Extract customer ID from user messages.

    Example 1:
    User: "I'm customer 12345"
    Output: 12345

    Example 2:
    User: "My account is ABC789"
    Output: ABC789

    User: %s
    Output:
    """.formatted(userMessage);
```

### 2. Error Handling

**Graceful Degradation**:
```java
try {
    return reActAgent.solve(question);
} catch (OpenAIException e) {
    log.error("OpenAI API failed", e);
    return fallbackToRuleBasedResponse(question);
}
```

**Retry with Backoff**:
```java
@Retryable(
    value = {OpenAIException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public String solve(String question) {
    return chatModel.chat(question);
}
```

**User-Friendly Messages**:
```java
// Good
"I'm having trouble connecting to the weather service. Please try again in a moment."

// Bad
"NullPointerException in WeatherTool.getCurrentWeather"
```

### 3. Memory Management

**Set Limits**:
```java
MessageWindowChatMemory.builder()
    .maxMessages(20) // Last 20 messages only
    .build();
```

**Compress Long Histories**:
```java
if (messageCount > 50) {
    summarizeAndCompress(sessionId);
}
```

**Clear Inactive Sessions**:
```java
@Scheduled(fixedRate = 3600000) // Every hour
public void cleanupInactiveSessions() {
    memoryService.clearSessionsOlderThan(Duration.ofHours(24));
}
```

### 4. Cost Optimization

**Use Cheaper Models for Routing**:
```java
// Use gpt-4o-mini for routing decisions
ChatModel routingModel = OpenAiChatModel.builder()
    .modelName("gpt-4o-mini")
    .build();

// Use gpt-4 for complex reasoning
ChatModel reasoningModel = OpenAiChatModel.builder()
    .modelName("gpt-4")
    .build();
```

**Cache Common Queries**:
```java
@Cacheable("agent-responses")
public String solve(String question) {
    // Expensive LLM call only happens on cache miss
    return reActAgent.solve(question);
}
```

**Limit Context Size**:
```java
// Only include last 5 messages in context
List<ChatMessage> recentHistory = getRecentMessages(sessionId, 5);
```

### 5. Security

**Sanitize Inputs**:
```java
public String process(String userInput) {
    String sanitized = sanitizeInput(userInput);
    return agent.solve(sanitized);
}

private String sanitizeInput(String input) {
    return input
        .replaceAll("[<>]", "")  // Remove HTML
        .replaceAll("javascript:", "")  // Remove JS
        .substring(0, Math.min(1000, input.length()));  // Limit length
}
```

**Validate Tool Calls**:
```java
@Tool("Gets customer info")
public String getCustomerInfo(String customerId, String requestingUserId) {
    if (!authService.canAccess(requestingUserId, customerId)) {
        return "Access denied";
    }
    // Proceed with query
}
```

**Rate Limit**:
```java
@RateLimiter(name = "agent-api", fallbackMethod = "rateLimitFallback")
public String execute(String message) {
    return agent.solve(message);
}
```

### 6. Testing

**Unit Test Tools**:
```java
@Test
void getCustomerInfo_ReturnsCorrectFormat() {
    String result = customerDataTool.getCustomerInfo("12345");
    assertThat(result).contains("Customer Information");
    assertThat(result).contains("12345");
}
```

**Integration Test Agents**:
```java
@SpringBootTest
@DirtiesContext
class ReActAgentIntegrationTest {
    @Autowired
    private ReActAgent agent;

    @Test
    void solve_UsesToolsCorrectly() {
        String response = agent.solve("What's the email for customer 12345?");
        assertThat(response).contains("@");
    }
}
```

**Mock External Services**:
```java
@MockBean
private ChatModel chatModel;

@Test
void agent_HandlesApiFailure() {
    when(chatModel.chat(any())).thenThrow(new RuntimeException("API down"));

    String response = agent.solve("test");

    assertThat(response).contains("error");
}
```

### 7. Monitoring

**Track Metrics**:
```java
meterRegistry.counter("agent.requests", "mode", mode).increment();
meterRegistry.timer("agent.duration", "mode", mode).record(duration);
```

**Log Agent Reasoning**:
```java
log.info("Agent reasoning: thought={}, action={}, observation={}",
    thought, action, observation);
```

**Alert on Failures**:
```java
if (failureRate > 0.05) {  // > 5% failure rate
    alertService.notify("High agent failure rate detected");
}
```

## Common Pitfalls and Solutions

### Pitfall 1: Unbounded Iteration Loops

**Problem**: ReAct agent gets stuck in infinite loop.

**Solution**: Always set `maxIterations`:
```java
public String solve(String question) {
    return solve(question, MAX_ITERATIONS);
}
```

### Pitfall 2: Context Window Overflow

**Problem**: Long conversation histories exceed token limits.

**Solution**: Implement message window or compression:
```java
MessageWindowChatMemory.builder()
    .maxMessages(20)
    .build();
```

### Pitfall 3: Poor Tool Descriptions

**Problem**: LLM selects wrong tool.

**Solution**: Write specific, detailed descriptions:
```java
@Tool("Retrieves CURRENT customer information by customer ID. Returns name, email, plan, and join date. Use this when user asks about account status or customer details.")
public String getCustomerInfo(String customerId) {
    // ...
}
```

### Pitfall 4: No Fallback for API Failures

**Problem**: Application crashes when OpenAI API is down.

**Solution**: Implement circuit breaker and fallback:
```java
@CircuitBreaker(name = "openai", fallbackMethod = "fallback")
public String solve(String question) {
    return agent.solve(question);
}

public String fallback(String question, Exception e) {
    return "Service temporarily unavailable. Please try again.";
}
```

## Summary

**Real-World Patterns**:
- Customer support automation with multi-step reasoning
- Technical documentation with RAG (retrieval + generation)
- Multi-step research with task decomposition
- Collaborative decision-making with multiple agents

**Best Practices**:
- Detailed, specific prompts with examples
- Graceful error handling and fallbacks
- Memory management with limits and compression
- Cost optimization with model selection and caching
- Security: sanitization, validation, rate limiting
- Comprehensive testing at all levels
- Monitoring and alerting for production

**Avoid Common Pitfalls**:
- Set iteration limits to prevent infinite loops
- Manage context window size
- Write detailed tool descriptions
- Implement fallbacks for API failures

You now have a production-ready agent system! Continue experimenting, iterate on your prompts, and monitor your agents in production to continually improve their performance.

---

**Congratulations!** You've completed the "From Chatbots to Autonomous Agents" tutorial. You're now equipped to build intelligent, autonomous AI systems that can reason, use tools, collaborate, and solve complex multi-step problems.
