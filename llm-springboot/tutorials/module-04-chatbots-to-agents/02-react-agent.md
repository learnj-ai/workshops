# The ReAct Agent Pattern

## Introduction

The **ReAct** (Reasoning + Acting) pattern is one of the most powerful agent architectures in modern AI systems. Unlike simple chatbots that just respond to questions, ReAct agents can **think iteratively**, **use tools** to gather information, and **adjust their approach** based on observations.

Think of ReAct as teaching an AI to be a detective: first, it thinks about what information it needs, then it takes action to get that information, observes the result, and repeats until it solves the case.

## What is ReAct?

ReAct stands for **Reasoning** and **Acting**. It's an iterative pattern where the agent:

1. **THOUGHT**: Reasons about what to do next
2. **ACTION**: Executes a tool or takes an action
3. **OBSERVATION**: Receives the result of the action
4. Repeats steps 1-3 until ready to provide a **FINAL ANSWER**

This pattern was introduced in the paper ["ReAct: Synergizing Reasoning and Acting in Language Models"](https://arxiv.org/abs/2210.03629) and has become fundamental to building autonomous agents.

### Why ReAct Matters

**Without ReAct** (traditional chatbot):
- User: "What's the weather in John's city?"
- Bot: "I don't have access to that information"

**With ReAct**:
- User: "What's the weather in customer 12345's city?"
- Agent THOUGHT: "I need to find the customer's city first"
- Agent ACTION: `getCustomerInfo(12345)`
- Agent OBSERVATION: "Customer lives in Seattle"
- Agent THOUGHT: "Now I can get Seattle's weather"
- Agent ACTION: `getCurrentWeather(Seattle)`
- Agent OBSERVATION: "Sunny, 72°F"
- Agent FINAL ANSWER: "The weather in Seattle (customer 12345's city) is sunny and 72°F"

## The ReActAgent Implementation

Let's examine the core components of our ReAct agent:

```java
@Service
public class ReActAgent {
    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);

    private static final String REACT_PROMPT = """
            You are a helpful AI assistant that uses tools to answer questions.

            You have access to the following tools:
            - getCustomerInfo(customerId): Get customer details by ID
            - searchTickets(status): Search support tickets (open/pending/closed)
            - getCurrentWeather(city): Get current weather for a city

            For each step, use this exact format:

            THOUGHT: [your reasoning about what to do]
            ACTION: [tool_name(parameters)]

            After receiving an observation, either:
            - Continue with another THOUGHT/ACTION if you need more information
            - Or provide the FINAL ANSWER when you have enough information

            FINAL ANSWER: [your complete answer to the user]

            Question: {question}

            {history}
            """;

    private final ChatModel chatModel;
    private final CustomerDataTool customerDataTool;
    private final WeatherTool weatherTool;

    @Value("${agent.react.max-iterations:5}")
    private int maxIterations;
}
```

### Key Design Elements

**Structured Prompting**: The prompt explicitly teaches the LLM the ReAct format. Notice how it:
- Defines the exact format: `THOUGHT:` then `ACTION:`
- Lists available tools with their signatures
- Explains when to continue vs. when to give a final answer

**Tool Injection**: The agent has direct access to tools via dependency injection:
- `CustomerDataTool`: Database queries for customer info
- `WeatherTool`: External API calls for weather data

**Configurable Iterations**: `max-iterations` prevents infinite loops—if the agent doesn't solve the problem in 5 steps, it returns partial progress.

## The Reasoning Loop

The heart of ReAct is the iterative reasoning loop:

```java
public String solve(String question, int maxIter) {
    log.info("Starting ReAct agent to solve: {}", question);

    StringBuilder history = new StringBuilder();
    String currentPrompt = REACT_PROMPT
            .replace("{question}", question)
            .replace("{history}", "");

    for (int iteration = 1; iteration <= maxIter; iteration++) {
        log.debug("Iteration {}/{}", iteration, maxIter);

        // Generate thought and action
        String response = chatModel.chat(currentPrompt);
        log.debug("LLM Response:\n{}", response);

        // Check if we have a final answer
        if (response.contains("FINAL ANSWER:")) {
            String finalAnswer = extractFinalAnswer(response);
            log.info("Reached final answer after {} iterations", iteration);
            return finalAnswer;
        }

        // Extract thought and action
        String thought = extractThought(response);
        String action = extractAction(response);

        if (action == null || action.isBlank()) {
            log.warn("No valid action found in iteration {}", iteration);
            continue;
        }

        log.debug("THOUGHT: {}", thought);
        log.debug("ACTION: {}", action);

        // Execute the action
        String observation = executeAction(action);
        log.debug("OBSERVATION: {}", observation);

        // Update history
        history.append("THOUGHT: ").append(thought).append("\n");
        history.append("ACTION: ").append(action).append("\n");
        history.append("OBSERVATION: ").append(observation).append("\n\n");

        // Update prompt with history
        currentPrompt = REACT_PROMPT
                .replace("{question}", question)
                .replace("{history}", history.toString());
    }

    // Reached max iterations without final answer
    log.warn("Reached max iterations ({}) without final answer", maxIter);
    return "I've analyzed the question but need more iterations to provide a complete answer. " +
           "Here's what I found:\n\n" + history.toString();
}
```

### Loop Breakdown

**Iteration Start**: Each iteration calls the LLM with the current prompt (which includes the full history of previous thoughts, actions, and observations).

**Response Parsing**: The agent checks if the LLM provided a `FINAL ANSWER:`. If yes, extraction and return. If no, it extracts the thought and action.

**Action Execution**: The action string (e.g., `getCustomerInfo(12345)`) is parsed and the corresponding tool is invoked.

**History Accumulation**: The thought, action, and observation are appended to history. This context is crucial—it helps the LLM "remember" what it's already tried.

**Prompt Update**: The prompt is rebuilt with the updated history, so the next iteration has full context.

**Termination**: Either the agent provides a final answer, or it hits max iterations and returns the partial work.

## Parsing and Extraction

The agent uses regex patterns to extract structured information from LLM responses:

```java
private String extractThought(String response) {
    Pattern pattern = Pattern.compile("THOUGHT:\\s*(.+?)(?=ACTION:|FINAL ANSWER:|$)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(response);
    if (matcher.find()) {
        return matcher.group(1).trim();
    }
    return "";
}

private String extractAction(String response) {
    Pattern pattern = Pattern.compile("ACTION:\\s*(.+?)(?=OBSERVATION:|THOUGHT:|FINAL ANSWER:|$)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(response);
    if (matcher.find()) {
        return matcher.group(1).trim();
    }
    return null;
}

private String extractFinalAnswer(String response) {
    Pattern pattern = Pattern.compile("FINAL ANSWER:\\s*(.+)", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(response);
    if (matcher.find()) {
        return matcher.group(1).trim();
    }
    return response;
}
```

### Parsing Strategy

**Greedy vs. Lazy Matching**: The patterns use `.+?` (lazy matching) to stop at the next section marker (like `ACTION:` or `FINAL ANSWER:`).

**DOTALL Flag**: Allows `.` to match newlines, so multi-line thoughts/actions are captured.

**Trimming**: Results are trimmed to remove leading/trailing whitespace.

**Fallbacks**: If a pattern doesn't match (e.g., no thought provided), it returns sensible defaults (empty string for thought, null for action).

## Tool Execution

The agent dynamically executes tools based on the LLM's action selection:

```java
private String executeAction(String action) {
    try {
        // Parse action format: tool_name(parameters)
        Pattern pattern = Pattern.compile("(\\w+)\\(([^)]*)\\)");
        Matcher matcher = pattern.matcher(action.trim());

        if (!matcher.find()) {
            return "Error: Invalid action format. Use tool_name(parameters)";
        }

        String toolName = matcher.group(1);
        String parameters = matcher.group(2).replaceAll("['\"]", "").trim();

        log.debug("Executing tool: {} with parameters: {}", toolName, parameters);

        return switch (toolName) {
            case "getCustomerInfo" -> customerDataTool.getCustomerInfo(parameters);
            case "searchTickets" -> customerDataTool.searchTickets(parameters);
            case "getCurrentWeather" -> weatherTool.getCurrentWeather(parameters);
            default -> "Error: Unknown tool: " + toolName;
        };

    } catch (Exception e) {
        log.error("Error executing action: {}", action, e);
        return "Error executing action: " + e.getMessage();
    }
}
```

### Execution Flow

**Action Parsing**: The action string is parsed to extract the tool name and parameters. For example, `getCustomerInfo('12345')` becomes:
- `toolName = "getCustomerInfo"`
- `parameters = "12345"` (after stripping quotes)

**Parameter Cleaning**: Quotes are removed because LLMs sometimes wrap parameters in single or double quotes.

**Tool Routing**: A switch expression routes to the appropriate tool method.

**Error Handling**: If parsing fails or the tool throws an exception, a descriptive error message is returned as the observation. This allows the agent to recover or try a different approach.

## Example ReAct Execution

Let's trace through a real example:

**User Question**: "How many open tickets does customer 12345 have?"

**Iteration 1**:
- **THOUGHT**: "I should first check if this customer exists and get their information"
- **ACTION**: `getCustomerInfo(12345)`
- **OBSERVATION**: "Customer Information: ID: 12345, Name: John Doe, Email: john@example.com, Subscription Plan: Enterprise, Member Since: 2023-01-15"

**Iteration 2**:
- **THOUGHT**: "The customer exists. Now I need to search for their open tickets"
- **ACTION**: `searchTickets(open)`
- **OBSERVATION**: "Found 3 open ticket(s): Ticket #101 - Customer: John Doe (ID: 12345) - Subject: Login issues..."

**Iteration 3**:
- **THOUGHT**: "I found tickets but need to count only those for customer 12345"
- **FINAL ANSWER**: "Customer 12345 (John Doe) currently has 3 open support tickets"

## Benefits of the ReAct Pattern

1. **Autonomous Problem Solving**: The agent figures out the sequence of actions needed without explicit programming
2. **Transparency**: Every step is logged (thought, action, observation), making debugging easy
3. **Tool Composability**: Multiple tools can be chained together (get customer info, then get weather for their city)
4. **Error Recovery**: If a tool fails, the observation contains the error, and the agent can try a different approach
5. **Flexible**: Works for a wide range of problems—the same pattern handles simple lookups and complex multi-step reasoning

## Best Practices

### Prompt Engineering

- **Be Explicit**: Clearly define the format the LLM should follow
- **Provide Examples**: In production, consider adding few-shot examples of correct reasoning
- **List Tools**: Always list available tools with their signatures and purposes

### Iteration Limits

- **Set Reasonable Limits**: 5-10 iterations is usually enough
- **Return Partial Progress**: If max iterations is hit, return what was accomplished
- **Monitor**: Log iteration counts to identify questions that consistently hit the limit

### Tool Design

- **Clear Names**: Use descriptive tool names like `getCustomerInfo` not `query1`
- **Simple Signatures**: Keep parameter lists simple; LLMs struggle with complex parameter structures
- **Good Error Messages**: Return actionable error messages that help the agent adjust

### Logging

- **Debug All Steps**: Log every thought, action, and observation during development
- **Production Filtering**: In production, log only final answers and errors (unless debugging)
- **Trace IDs**: Add trace IDs to correlate all logs from a single execution

## Common Pitfalls

1. **Infinite Loops**: Always set `max-iterations` to prevent runaway loops
2. **Poor Parsing**: Test regex patterns with various LLM outputs; GPT-4 is reliable, but smaller models may format inconsistently
3. **Tool Errors**: Handle tool exceptions gracefully—return error messages, don't crash
4. **Context Window**: Long histories can exceed context limits; consider truncation strategies for very long conversations
5. **Cost**: Each iteration is an LLM call; monitor costs for high-volume applications

## Testing ReAct Agents

Test different scenarios:

```java
@Test
void testSimpleLookup() {
    String answer = reActAgent.solve("What is the email for customer 12345?");
    assertThat(answer).contains("@");
}

@Test
void testMultiStepReasoning() {
    String answer = reActAgent.solve(
        "What's the weather in the city where customer 12345 lives?"
    );
    assertThat(answer).containsAnyOf("sunny", "cloudy", "rainy");
}

@Test
void testMaxIterations() {
    String answer = reActAgent.solve("Impossible question", 2);
    assertThat(answer).contains("need more iterations");
}
```

## Summary

The ReAct pattern is a foundational building block for autonomous agents. By combining reasoning (thinking about what to do) with acting (using tools to gather information), you create systems that can solve complex, multi-step problems.

Key takeaways:
- ReAct = iterative loop of THOUGHT → ACTION → OBSERVATION
- Structured prompts guide the LLM to follow the pattern
- Tool execution enables agents to interact with the real world
- History accumulation provides context for multi-step reasoning
- Iteration limits prevent infinite loops

In the next chapter, we'll explore **specialized agents** that focus on specific domains, and how to orchestrate multiple agents to collaborate on complex problems.

---

**Next Chapter**: [03 - Specialized Agents](./03-specialized-agents.md)
