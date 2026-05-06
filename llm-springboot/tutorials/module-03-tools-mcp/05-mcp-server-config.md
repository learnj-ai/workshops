# Chapter: MCPServerConfig - Model Context Protocol Configuration

## Introduction: Configuring Your AI Brain

Before your AI assistant can use tools, it needs a brain—a language model that can understand requests, reason about which tools to use, and synthesize responses. **MCPServerConfig** is where you configure that brain: the OpenAI ChatModel that powers your assistant.

MCP stands for **Model Context Protocol**, an emerging standard for how AI models discover and interact with tools. While LangChain4j handles most of the MCP complexity behind the scenes, this configuration class is where you set up the foundation: the chat model itself.

Think of this as configuring the central processor of your AI system—everything else (tools, orchestration, responses) flows through this component.

## Code Deep Dive

Let's examine the MCPServerConfig implementation:

```java
package com.techcorp.assistant.module03.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * MCP (Model Context Protocol) Server Configuration.
 *
 * This configuration registers tools with OpenAI's chat model,
 * making them discoverable and executable by the LLM during conversations.
 */
@Configuration
public class MCPServerConfig {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Value("${openai.model.name:gpt-4o-mini}")
    private String modelName;

    /**
     * Creates a chat model bean for use throughout the application.
     *
     * Tools will be registered with this model via the ToolOrchestrator service.
     *
     * @return Configured ChatModel (OpenAI implementation)
     */
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(modelName)
                .temperature(0.7)
                .timeout(Duration.ofSeconds(60))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
```

## Architecture and Design Decisions

### Spring Configuration Class
```java
@Configuration
public class MCPServerConfig {
```

**@Configuration** marks this as a Spring configuration class:
- Spring scans for `@Bean` methods during application startup
- Beans defined here are available for dependency injection throughout the app
- Centralizes configuration in one place
- Enables type-safe configuration over property files

### Externalized Configuration
```java
@Value("${openai.api.key}")
private String openAiApiKey;

@Value("${openai.model.name:gpt-4o-mini}")
private String modelName;
```

**Why inject from properties?**
- **Security**: API keys never appear in source code
- **Flexibility**: Different keys for dev/staging/production
- **Easy switching**: Change models without recompiling

**The `:gpt-4o-mini` syntax** provides a default value if the property isn't set.

In `application.properties`:
```properties
openai.api.key=${OPENAI_API_KEY:your-api-key-here}
openai.model.name=gpt-4o-mini
```

This creates a fallback chain:
1. Environment variable `OPENAI_API_KEY`
2. Property `openai.api.key` from application.properties
3. Default value `your-api-key-here` (for local dev only!)

### Bean Definition
```java
@Bean
public ChatModel chatModel() {
    return OpenAiChatModel.builder()
            // ... configuration
            .build();
}
```

**Why define ChatModel as a bean?**
- **Single instance**: Spring creates one ChatModel and reuses it (singleton scope by default)
- **Dependency injection**: Other components can simply declare `ChatModel chatModel` and Spring injects it
- **Lifecycle management**: Spring handles initialization and cleanup
- **Easy testing**: Can provide mock ChatModel in tests

**Return type is interface (ChatModel), not implementation (OpenAiChatModel):**
- Makes it easy to swap providers (OpenAI, Anthropic, Azure, local models)
- Components that use it depend on the interface, not the concrete class
- Classic dependency inversion principle

## Model Configuration Parameters

### API Key
```java
.apiKey(openAiApiKey)
```

Your OpenAI API key. Get one from https://platform.openai.com/api-keys

**Security best practices:**
- Never commit API keys to Git
- Use environment variables in production
- Rotate keys periodically
- Monitor API usage for anomalies
- Set up billing alerts

### Model Name
```java
.modelName(modelName)  // "gpt-4o-mini"
```

Which OpenAI model to use. Options include:

| Model | Cost | Speed | Quality | Tool Use |
|-------|------|-------|---------|----------|
| **gpt-4o-mini** | $$$ | Fast | Excellent | Native |
| **gpt-4o** | $$$$ | Medium | Best | Native |
| **gpt-4-turbo** | $$$ | Medium | Excellent | Native |
| **gpt-3.5-turbo** | $ | Very Fast | Good | Native |

**For this workshop, gpt-4o-mini is ideal:**
- Cost-effective for learning and development
- Fast enough for interactive applications
- Excellent at function calling
- Good reasoning for tool selection

**In production, consider:**
- gpt-4o for complex reasoning or high-stakes decisions
- gpt-4o-mini for most customer service scenarios
- gpt-3.5-turbo for simple, high-volume queries

### Temperature
```java
.temperature(0.7)
```

Controls randomness in responses. Range: 0.0 to 2.0

- **0.0 - 0.3**: Deterministic, factual, consistent
  - Good for: customer support, data retrieval, question answering
  - "Customer 12345's email is alice@example.com"

- **0.4 - 0.7**: Balanced creativity and consistency
  - Good for: general conversation, explanations, recommendations
  - "Customer 12345 (Alice Johnson) can be reached at alice@example.com"

- **0.8 - 1.5**: Creative, varied, exploratory
  - Good for: content generation, brainstorming, storytelling
  - "Alice Johnson, one of our valued premium members, uses alice@example.com"

**Why 0.7 is a good default:**
- Responses feel natural and conversational
- Still consistent enough for factual queries
- Provides variety without unpredictability
- Works well for mixed-purpose assistants

### Timeout
```java
.timeout(Duration.ofSeconds(60))
```

Maximum time to wait for a response from OpenAI's API.

**Why 60 seconds?**
- Tool execution adds latency (database queries, API calls)
- The LLM might make multiple tool calls in sequence
- Network latency varies
- Complex queries take longer to process

**Timeout considerations:**
- **Too short**: Requests fail prematurely, frustrating users
- **Too long**: Users wait too long, poor UX
- **Production**: Start with 60s, tune based on actual latency metrics

**Example flow timing:**
```
User query arrives: 0ms
↓
Send to OpenAI: 50ms (network)
↓
LLM processing: 500ms
↓
LLM requests tool call: 550ms
↓
Execute getCustomerInfo(): 200ms (database query)
↓
Return to LLM: 750ms
↓
LLM generates response: 1000ms
↓
Return to user: 1050ms (network)
```

Total: ~1 second (well under 60s timeout)

### Request/Response Logging
```java
.logRequests(true)
.logResponses(true)
```

Logs all API interactions for debugging and monitoring.

**What gets logged:**

**Request log example:**
```
DEBUG dev.langchain4j.model.openai.OpenAiChatModel - Request:
{
  "model": "gpt-4o-mini",
  "messages": [
    {"role": "user", "content": "What's customer 12345's email?"}
  ],
  "tools": [
    {
      "type": "function",
      "function": {
        "name": "getCustomerInfo",
        "description": "Retrieves customer information by customer ID...",
        "parameters": {...}
      }
    }
  ],
  "temperature": 0.7
}
```

**Response log example:**
```
DEBUG dev.langchain4j.model.openai.OpenAiChatModel - Response:
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "tool_calls": [
          {
            "function": {
              "name": "getCustomerInfo",
              "arguments": "{\"customerId\":\"12345\"}"
            }
          }
        ]
      }
    }
  ]
}
```

**Benefits of logging:**
- **Debugging**: See exactly what the LLM is trying to do
- **Optimization**: Identify slow queries or excessive tool calls
- **Cost monitoring**: Track token usage and API calls
- **Quality assurance**: Verify the LLM is making correct decisions

**Production considerations:**
- Logs contain user data—ensure proper privacy controls
- Large logs can impact performance—consider sampling
- Set appropriate log levels (DEBUG in dev, INFO in prod)
- Redact sensitive information if needed

## Model Context Protocol (MCP) Explained

### What is MCP?

The **Model Context Protocol** is a standardized way for AI models to discover and invoke tools. While not yet a formal standard, the concept is implemented by LangChain4j and similar frameworks.

**Key MCP concepts:**

1. **Tool Discovery**: The LLM is told about available tools via JSON schemas
2. **Tool Invocation**: The LLM generates structured calls (function name + arguments)
3. **Result Handling**: Tool results are fed back to the LLM for synthesis
4. **Multi-turn Conversations**: The LLM can call multiple tools in sequence

### How MCP Works in LangChain4j

When you create this ChatModel bean and register tools (in ToolOrchestrator), LangChain4j:

1. **Generates tool schemas** from `@Tool` annotations:
```json
{
  "type": "function",
  "function": {
    "name": "getCustomerInfo",
    "description": "Retrieves customer information by customer ID including name, email, and subscription plan",
    "parameters": {
      "type": "object",
      "properties": {
        "customerId": {
          "type": "string",
          "description": "The customer ID to retrieve information for"
        }
      },
      "required": ["customerId"]
    }
  }
}
```

2. **Sends schemas to OpenAI** with every request (via the `tools` parameter)

3. **Monitors LLM responses** for tool calls:
```json
{
  "role": "assistant",
  "tool_calls": [
    {
      "id": "call_abc123",
      "type": "function",
      "function": {
        "name": "getCustomerInfo",
        "arguments": "{\"customerId\":\"12345\"}"
      }
    }
  ]
}
```

4. **Invokes your Java methods** with the extracted arguments

5. **Returns results to the LLM**:
```json
{
  "role": "tool",
  "tool_call_id": "call_abc123",
  "content": "Customer Information:\n- ID: 12345\n- Name: Alice Johnson\n..."
}
```

6. **LLM generates final response** using the tool data

### Why This Matters

Without MCP, you'd have to:
- Manually parse user queries to detect tool needs
- Write complex regex or NLP to extract parameters
- Handle errors and edge cases yourself
- Synthesize responses manually

With MCP (via LangChain4j):
- The LLM decides when to use tools autonomously
- Parameter extraction is handled by the LLM's understanding
- Multi-step reasoning works out of the box
- Natural language synthesis is automatic

## Alternative Model Providers

While this workshop uses OpenAI, LangChain4j supports many providers:

### Azure OpenAI
```java
@Bean
public ChatModel chatModel() {
    return AzureOpenAiChatModel.builder()
            .endpoint(azureEndpoint)
            .apiKey(azureApiKey)
            .deploymentName("gpt-4o-mini")
            .temperature(0.7)
            .build();
}
```

### Anthropic Claude
```java
@Bean
public ChatModel chatModel() {
    return AnthropicChatModel.builder()
            .apiKey(anthropicApiKey)
            .modelName("claude-3-5-sonnet-20241022")
            .temperature(0.7)
            .build();
}
```

### Local Models (Ollama)
```java
@Bean
public ChatModel chatModel() {
    return OllamaChatModel.builder()
            .baseUrl("http://localhost:11434")
            .modelName("llama3.2")
            .temperature(0.7)
            .build();
}
```

**To switch providers:** Just change the bean definition. The rest of your code (tools, orchestrator, controller) stays the same!

## Configuration Best Practices

### Environment-Specific Properties

**application-dev.properties:**
```properties
openai.api.key=sk-test-key-for-development
openai.model.name=gpt-3.5-turbo
# Cheaper model for development
```

**application-prod.properties:**
```properties
openai.api.key=${OPENAI_API_KEY}
openai.model.name=gpt-4o-mini
# Production-grade model
```

### Profiles
```java
@Configuration
@Profile("production")
public class ProductionMCPConfig {
    @Bean
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.5)  // More deterministic in prod
                .timeout(Duration.ofSeconds(30))  // Tighter timeout
                .logRequests(false)  // Reduce log volume
                .logResponses(false)
                .build();
    }
}
```

## Key Takeaways

- **MCPServerConfig creates the ChatModel bean** that powers the entire AI system
- **@Configuration and @Bean** make the model available for dependency injection
- **Configuration is externalized** via @Value and application.properties
- **Temperature controls creativity** - 0.7 is a balanced default
- **Timeout prevents hanging requests** - 60s accommodates tool execution
- **Logging aids debugging** but should be controlled in production
- **MCP enables tool calling** by sending tool schemas to the LLM
- **The ChatModel interface** allows easy switching between providers
- **Security is critical** - never hardcode API keys

## Next Steps: Orchestrating Tool Execution

Now that you have a configured ChatModel, you need to connect it to your tools and handle the orchestration flow.

In the next chapter, **ToolOrchestrator**, you'll learn how to:
- Use AiServices to register tools with the ChatModel
- Handle automatic tool execution during conversations
- Manage the multi-turn flow when tools are invoked
- Build the core service that ties everything together

---

**Continue to the next chapter to see the magic of automatic tool orchestration!**
