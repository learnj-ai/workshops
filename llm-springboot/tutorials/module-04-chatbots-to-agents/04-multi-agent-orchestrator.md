# Multi-Agent Orchestration

## Introduction

Now that we have specialized agents, we need an **orchestrator** to coordinate them. The `MultiAgentOrchestrator` is the traffic controller of our agent system—it decides which specialist to route requests to, and can even gather perspectives from multiple agents for collaborative problem-solving.

In this chapter, you'll learn two orchestration patterns: **routing** (selecting the best single agent) and **collaboration** (synthesizing responses from all agents).

## The MultiAgentOrchestrator

```java
@Service
public class MultiAgentOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(MultiAgentOrchestrator.class);

    private final ChatModel coordinatorModel;
    private final Map<String, SpecializedAgent> agents;

    public MultiAgentOrchestrator(
            ChatModel coordinatorModel,
            CustomerSupportAgent customerSupportAgent,
            TechnicalDocAgent technicalDocAgent,
            ProductExpertAgent productExpertAgent) {

        this.coordinatorModel = coordinatorModel;
        this.agents = new HashMap<>();
        this.agents.put("support", customerSupportAgent);
        this.agents.put("documentation", technicalDocAgent);
        this.agents.put("product", productExpertAgent);

        log.info("MultiAgentOrchestrator initialized with {} agents", agents.size());
    }
}
```

### Design Elements

**Coordinator Model**: The orchestrator has its own `ChatModel` instance used for routing decisions and synthesis. This could use a different model than the specialized agents (e.g., a faster, cheaper model for routing).

**Agent Registry**: A `Map<String, SpecializedAgent>` stores all available agents with simple string keys ("support", "documentation", "product"). This makes routing decisions straightforward.

**Constructor Injection**: All specialized agents are injected via constructor, ensuring they're available at initialization time.

**Logging**: The orchestrator logs its initialization, making it easy to verify all agents are registered.

## Pattern 1: Routing

The routing pattern selects the **single best agent** for a request:

```java
public String routeRequest(String request) {
    log.info("Routing request: {}", request);

    // Build routing prompt
    String agentDescriptions = agents.entrySet().stream()
            .map(entry -> String.format("- %s: %s",
                    entry.getKey(),
                    entry.getValue().getDescription()))
            .collect(Collectors.joining("\n"));

    String routingPrompt = String.format("""
            Analyze this user request and select the most appropriate agent to handle it.

            Available agents:
            %s

            User request: "%s"

            Respond with ONLY the agent key (support, documentation, or product).
            If uncertain, default to 'support'.
            """, agentDescriptions, request);

    String selectedKey = coordinatorModel.chat(routingPrompt).trim().toLowerCase();

    // Clean up response (remove quotes, extra words, etc.)
    selectedKey = selectedKey.replaceAll("[\"']", "").split("\\s+")[0];

    // Validate and default
    if (!agents.containsKey(selectedKey)) {
        log.warn("Invalid agent key '{}', defaulting to support", selectedKey);
        selectedKey = "support";
    }

    log.info("Selected agent: {}", selectedKey);
    SpecializedAgent selectedAgent = agents.get(selectedKey);

    return selectedAgent.process(request);
}
```

### How Routing Works

**Step 1: Build Agent Descriptions**

The orchestrator gathers descriptions from all agents:
```
- support: Handles customer account queries, support tickets, and account-related issues
- documentation: Provides technical documentation, API references, and configuration guidance
- product: Expert on product features, pricing plans, and capabilities
```

**Step 2: Create Routing Prompt**

The prompt presents the user's request and asks the LLM to select the best agent. Notice:
- Clear instruction: "Respond with ONLY the agent key"
- Default fallback: "If uncertain, default to 'support'"
- Explicit listing of available agents

**Step 3: Parse Response**

The LLM returns a key like "documentation" or "product". We clean it:
- `.trim().toLowerCase()`: Normalize whitespace and case
- `.replaceAll("[\"']", "")`: Remove quotes (LLMs sometimes add them)
- `.split("\\s+")[0]`: Take first word only (in case LLM adds explanation)

**Step 4: Validate and Default**

If the key doesn't match any agent, default to "support" (a safe fallback for general queries).

**Step 5: Execute**

Call the selected agent's `process()` method with the original request.

### Example Routing Flow

**User Request**: "How do I configure OAuth authentication?"

**Agent Descriptions**:
```
- support: Handles customer account queries...
- documentation: Provides technical documentation...
- product: Expert on product features...
```

**Routing Prompt**: (shown above with the request inserted)

**LLM Response**: "documentation"

**Selected Agent**: `TechnicalDocAgent`

**Final Response**: "To configure OAuth authentication, you need to set the following properties in your application.yml..."

### Benefits of Routing

1. **Efficient**: Only one agent processes the request (lower cost, faster response)
2. **Focused**: The user gets an expert response from the most relevant specialist
3. **Scalable**: Add new agents to the registry without changing routing logic
4. **Auditable**: Every routing decision is logged

## Pattern 2: Collaboration

The collaborative pattern gathers perspectives from **all agents** and synthesizes them:

```java
public String collaborativeRequest(String request) {
    log.info("Processing collaborative request: {}", request);

    // Gather responses from all agents
    Map<String, String> agentResponses = new HashMap<>();

    for (Map.Entry<String, SpecializedAgent> entry : agents.entrySet()) {
        String agentKey = entry.getKey();
        SpecializedAgent agent = entry.getValue();

        log.debug("Getting perspective from {}", agent.getName());
        String response = agent.process(request);
        agentResponses.put(agentKey, response);
    }

    // Synthesize responses
    String synthesisPrompt = String.format("""
            Multiple experts have provided perspectives on this question:

            Question: %s

            Customer Support perspective:
            %s

            Technical Documentation perspective:
            %s

            Product Expert perspective:
            %s

            Synthesize these perspectives into a single, comprehensive response.
            Highlight complementary insights and resolve any contradictions.
            Provide a unified answer that leverages all expert knowledge.
            """,
            request,
            agentResponses.get("support"),
            agentResponses.get("documentation"),
            agentResponses.get("product")
    );

    String synthesizedResponse = coordinatorModel.chat(synthesisPrompt);
    log.info("Synthesized collaborative response");

    return synthesizedResponse;
}
```

### How Collaboration Works

**Step 1: Gather Perspectives**

The orchestrator calls `process()` on every agent, collecting their individual responses in a map.

**Step 2: Build Synthesis Prompt**

The prompt presents:
- The original question
- Each agent's full response
- Instructions to synthesize, highlight insights, and resolve contradictions

**Step 3: Synthesize**

The coordinator model (GPT-4) reads all perspectives and creates a unified answer that combines the best insights from each specialist.

### Example Collaboration Flow

**User Request**: "What's included in the Enterprise plan and how do I configure it?"

This question spans multiple domains:
- **Product**: What features are included?
- **Documentation**: How to configure?
- **Support**: How to upgrade?

**Agent Responses**:

*Support*: "The Enterprise plan includes priority support, dedicated account manager, and SSO. To upgrade, contact sales@techcorp.com."

*Documentation*: "To configure Enterprise features, set `plan.level=enterprise` in your application.yml and configure SSO via the `/admin/sso` endpoint."

*Product*: "Enterprise plan includes: unlimited users, advanced analytics, SSO, API access, custom integrations, 99.9% SLA, and dedicated infrastructure."

**Synthesis Prompt**: (combines all three responses with synthesis instructions)

**Synthesized Response**: "The Enterprise plan provides unlimited users, advanced analytics, SSO, API access, custom integrations, 99.9% SLA, dedicated infrastructure, priority support, and a dedicated account manager. To configure, set `plan.level=enterprise` in application.yml and configure SSO via `/admin/sso`. For upgrades, contact sales@techcorp.com."

### Benefits of Collaboration

1. **Comprehensive**: Combines expertise from all domains
2. **Resolves Ambiguity**: If agents provide contradictory info, synthesis can clarify
3. **Cross-Domain**: Handles questions that span multiple specialties
4. **Higher Quality**: Multiple perspectives often yield better answers than any single agent

### Trade-offs

**Cost**: Calls every agent PLUS a synthesis step (4 LLM calls total with 3 agents)
**Latency**: Sequential agent calls take longer than single routing
**Complexity**: More moving parts, more potential failure points

Use collaboration for:
- Complex, multi-domain questions
- High-value queries where quality matters most
- User research or decision-making scenarios

Use routing for:
- Simple, single-domain questions
- High-volume, cost-sensitive applications
- Fast response time requirements

## Advanced: Parallel Agent Execution

The current implementation calls agents sequentially. For better performance, you can parallelize:

```java
public String collaborativeRequest(String request) {
    log.info("Processing collaborative request: {}", request);

    // Parallel execution using CompletableFuture
    Map<String, CompletableFuture<String>> futures = new HashMap<>();

    for (Map.Entry<String, SpecializedAgent> entry : agents.entrySet()) {
        String agentKey = entry.getKey();
        SpecializedAgent agent = entry.getValue();

        CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
            log.debug("Getting perspective from {}", agent.getName());
            return agent.process(request);
        });

        futures.put(agentKey, future);
    }

    // Wait for all agents to complete
    Map<String, String> agentResponses = new HashMap<>();
    for (Map.Entry<String, CompletableFuture<String>> entry : futures.entrySet()) {
        try {
            agentResponses.put(entry.getKey(), entry.getValue().get());
        } catch (Exception e) {
            log.error("Agent {} failed", entry.getKey(), e);
            agentResponses.put(entry.getKey(), "Error: Agent failed to respond");
        }
    }

    // Synthesis step (same as before)
    // ...
}
```

This reduces latency from `3 × agent_time` to `max(agent_times)`.

## Dynamic Agent Selection

For more flexibility, you can let the LLM select **multiple** agents instead of all-or-one:

```java
String selectionPrompt = String.format("""
        Analyze this request and select which agents should be consulted.

        Available agents:
        %s

        User request: "%s"

        Respond with a comma-separated list of agent keys (e.g., "support,product").
        Select only the agents whose expertise is relevant.
        """, agentDescriptions, request);

String selected = coordinatorModel.chat(selectionPrompt);
List<String> selectedKeys = Arrays.asList(selected.split(","))
        .stream()
        .map(String::trim)
        .collect(Collectors.toList());

// Call only selected agents
for (String key : selectedKeys) {
    if (agents.containsKey(key)) {
        String response = agents.get(key).process(request);
        agentResponses.put(key, response);
    }
}
```

This balances cost (don't call all agents) with quality (call multiple if needed).

## Adding New Agents

To add a new agent to the orchestrator:

**Step 1**: Create the agent class implementing `SpecializedAgent`

**Step 2**: Inject it in the orchestrator constructor:

```java
public MultiAgentOrchestrator(
        ChatModel coordinatorModel,
        CustomerSupportAgent customerSupportAgent,
        TechnicalDocAgent technicalDocAgent,
        ProductExpertAgent productExpertAgent,
        BillingAgent billingAgent) { // NEW

    this.coordinatorModel = coordinatorModel;
    this.agents = new HashMap<>();
    this.agents.put("support", customerSupportAgent);
    this.agents.put("documentation", technicalDocAgent);
    this.agents.put("product", productExpertAgent);
    this.agents.put("billing", billingAgent); // NEW

    log.info("MultiAgentOrchestrator initialized with {} agents", agents.size());
}
```

**Step 3**: Update synthesis prompt if using collaboration (add billing perspective).

That's it! The routing logic automatically picks up the new agent via its description.

## Testing the Orchestrator

```java
@Test
void routeRequest_SelectsCorrectAgent() {
    String response = orchestrator.routeRequest("What's the status of my account?");
    // Should route to support agent
    assertThat(response).isNotEmpty();
}

@Test
void collaborativeRequest_SynthesizesMultiplePerspectives() {
    String response = orchestrator.collaborativeRequest(
        "What's in the Enterprise plan and how do I set it up?"
    );
    // Should contain product info AND configuration steps
    assertThat(response).containsIgnoringCase("enterprise");
    assertThat(response).containsAnyOf("configure", "setup", "application.yml");
}

@Test
void routeRequest_DefaultsToSupport_OnAmbiguousQuery() {
    String response = orchestrator.routeRequest("Help!");
    // Ambiguous query should default to support
    assertThat(response).isNotEmpty();
}
```

## Best Practices

### Routing Prompts

- **Be explicit**: Clearly list agent options
- **Provide defaults**: Always have a fallback
- **Format constraints**: Ask for "ONLY the key" to simplify parsing

### Synthesis Prompts

- **Show all perspectives**: Include each agent's full response
- **Give clear instructions**: "Synthesize", "highlight insights", "resolve contradictions"
- **Request unified output**: Ask for a single cohesive answer, not a summary of summaries

### Error Handling

- **Validate agent keys**: Check if the key exists before calling the agent
- **Default gracefully**: If routing fails, pick a sensible default (usually support)
- **Log decisions**: Always log which agent was selected and why
- **Handle agent failures**: If an agent throws an exception during collaboration, log it and continue with other agents

### Performance

- **Cache descriptions**: Agent descriptions don't change, so build the description string once
- **Parallelize**: Use `CompletableFuture` for collaborative requests
- **Monitor costs**: Track how often you use collaboration vs. routing
- **Set timeouts**: Don't let a slow agent block the entire request

## Summary

The `MultiAgentOrchestrator` provides two powerful patterns:

**Routing**: Select the single best agent for efficient, focused responses
**Collaboration**: Gather perspectives from all agents for comprehensive, cross-domain answers

Key concepts:
- Agent registry maps keys to specialist instances
- Routing uses LLM to analyze requests and select the best agent
- Collaboration gathers all responses and synthesizes them
- Parallelization reduces latency for collaborative requests
- Adding new agents is straightforward—just register them in the map

In the next chapter, we'll explore **conversation memory** to make our agents stateful and context-aware across multiple turns.

---

**Next Chapter**: [05 - Conversation Memory](./05-conversation-memory.md)
