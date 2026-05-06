# LLM Config

## Overview

The `LLMConfig` class configures **dual LLM models** with different purposes: a primary model for generating responses and a separate validator model for security checks. This separation of concerns is a critical security pattern that prevents a compromised primary model from validating its own malicious output.

## Why Dual Models?

### The Security Problem

Using a single model for both generation and validation creates a vulnerability:

```
Compromised Primary Model:
- Generates malicious response: "Your password is 12345"
- Validates its own output: "This response is safe ✓"
- Security bypass complete!
```

### The Solution: Separation of Concerns

```
Primary Model (Generation):
- Task: Generate helpful responses
- Model: GPT-4 (high quality, more expensive)
- Temperature: 0.7 (creative, varied responses)

Validator Model (Security):
- Task: Validate safety and accuracy
- Model: GPT-3.5-turbo (fast, cheaper)
- Temperature: 0.0 (deterministic, consistent)
```

**Benefits**:
1. **Security**: Validator cannot be manipulated by primary model
2. **Cost Optimization**: Use cheaper model for simple validation tasks
3. **Performance**: Faster model for validation reduces latency
4. **Specialization**: Different configurations for different purposes

## Implementation

### Location
```
/src/main/java/com/techcorp/assistant/module05/config/LLMConfig.java
```

### Core Code

```java
@Configuration
public class LLMConfig {

    @Value("${openai.api.key}")
    private String openAiApiKey;

    @Value("${openai.model.name}")
    private String modelName;

    @Value("${openai.validator.model.name}")
    private String validatorModelName;

    @Value("${openai.validator.model.temperature}")
    private Double validatorTemperature;

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

    @Bean
    public ChatModel validatorChatModel() {
        return OpenAiChatModel.builder()
                .apiKey(openAiApiKey)
                .modelName(validatorModelName)
                .temperature(validatorTemperature)
                .timeout(Duration.ofSeconds(30))
                .logRequests(false)
                .logResponses(false)
                .build();
    }
}
```

## Configuration Breakdown

### Primary Chat Model

```java
@Bean
public ChatModel chatModel() {
    return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(modelName)  // "gpt-4" or "gpt-4-turbo"
            .temperature(0.7)      // Creative responses
            .timeout(Duration.ofSeconds(60))  // Longer timeout
            .logRequests(true)     // Debug full conversations
            .logResponses(true)
            .build();
}
```

**Configuration rationale**:

**Model**: `gpt-4` or `gpt-4-turbo`
- High-quality responses
- Better reasoning capabilities
- More expensive but worth it for primary generation

**Temperature**: `0.7`
- Balanced creativity
- Varied responses to same question
- Natural conversational style

**Timeout**: `60 seconds`
- Longer timeout for complex queries
- Allows for longer responses
- Handles multi-step reasoning

**Logging**: `true`
- Full request/response logging for debugging
- Helps troubleshoot prompt issues
- Disable in production for performance

### Validator Chat Model

```java
@Bean
public ChatModel validatorChatModel() {
    return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(validatorModelName)  // "gpt-3.5-turbo"
            .temperature(validatorTemperature)  // 0.0
            .timeout(Duration.ofSeconds(30))  // Shorter timeout
            .logRequests(false)    // Reduced logging
            .logResponses(false)
            .build();
}
```

**Configuration rationale**:

**Model**: `gpt-3.5-turbo`
- Faster inference (~2-3x faster than GPT-4)
- Cheaper (~10x cheaper than GPT-4)
- Sufficient for binary classification tasks
- Good at following structured output instructions

**Temperature**: `0.0`
- Deterministic responses
- Consistent validation decisions
- Eliminates randomness in safety checks
- Same input always produces same validation

**Timeout**: `30 seconds`
- Validation should be quick
- Shorter timeout prevents hanging
- Forces fast, decisive answers

**Logging**: `false`
- Reduces log volume (validation happens frequently)
- Better performance
- Enable selectively for debugging

## Application Properties

```properties
# OpenAI API Configuration
openai.api.key=${OPENAI_API_KEY}

# Primary model for response generation
openai.model.name=gpt-4-turbo

# Validator model for security checks
openai.validator.model.name=gpt-3.5-turbo
openai.validator.model.temperature=0.0
```

### Environment Variables

```bash
# Set via environment
export OPENAI_API_KEY=sk-...

# Or in .env file
OPENAI_API_KEY=sk-...
```

**Security best practice**: Never commit API keys to version control. Always use environment variables or secret management systems.

## Usage Examples

### Dependency Injection

```java
@Service
public class SimpleRAGService {

    private final ChatModel chatModel;  // Primary model

    public SimpleRAGService(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public String generate(String prompt) {
        return chatModel.chat(prompt);
    }
}
```

```java
@Service
public class OutputValidator {

    private final ChatModel validatorChatModel;  // Validator model

    public OutputValidator(@Qualifier("validatorChatModel") ChatModel validatorChatModel) {
        this.validatorChatModel = validatorChatModel;
    }

    public ValidationResult validate(String output) {
        String prompt = buildValidationPrompt(output);
        String result = validatorChatModel.chat(prompt);
        return parseResult(result);
    }
}
```

**Key point**: Use `@Qualifier("validatorChatModel")` to inject the validator model specifically.

### Model Selection Strategy

```java
@Configuration
public class LLMConfig {

    @Bean
    @Primary  // Default injection
    public ChatModel chatModel() {
        return OpenAiChatModel.builder()
            .modelName(modelName)
            .build();
    }

    @Bean("validatorChatModel")
    public ChatModel validatorChatModel() {
        return OpenAiChatModel.builder()
            .modelName(validatorModelName)
            .build();
    }

    @Bean("fastChatModel")  // Additional model for quick tasks
    public ChatModel fastChatModel() {
        return OpenAiChatModel.builder()
            .modelName("gpt-3.5-turbo")
            .temperature(0.5)
            .build();
    }
}
```

## Cost Optimization

### Model Selection by Use Case

| Use Case | Model | Rationale |
|----------|-------|-----------|
| Primary generation | GPT-4 Turbo | Highest quality, worth the cost |
| Safety validation | GPT-3.5 Turbo | Fast, cheap, good at classification |
| Hallucination check | GPT-3.5 Turbo | Fact-checking doesn't need GPT-4 |
| PII detection | Regex patterns | No LLM needed, instant, free |
| Prompt injection | Pattern matching | Rules-based, no API cost |

### Cost Calculation

**Scenario**: 1000 queries/day

**Without dual models** (all GPT-4):
- Generation: 1000 × $0.01 = $10/day
- Validation: 1000 × $0.01 = $10/day
- **Total**: $20/day = $600/month

**With dual models** (GPT-4 + GPT-3.5):
- Generation: 1000 × $0.01 = $10/day
- Validation: 1000 × $0.001 = $1/day
- **Total**: $11/day = $330/month
- **Savings**: $270/month (45% reduction)

### Conditional Model Usage

```java
@Service
public class AdaptiveRAGService {

    private final ChatModel gpt4Model;
    private final ChatModel gpt35Model;

    public String generate(String query, UserContext user) {
        // Use cheaper model for simple queries
        if (isSimpleQuery(query)) {
            return gpt35Model.chat(query);
        }

        // Use premium model for complex queries or premium users
        if (isComplexQuery(query) || user.isPremium()) {
            return gpt4Model.chat(query);
        }

        return gpt35Model.chat(query);
    }

    private boolean isSimpleQuery(String query) {
        return query.split("\\s+").length < 10 &&
               !query.contains("analyze") &&
               !query.contains("compare");
    }
}
```

## Advanced Configuration

### Retry and Fallback

```java
@Bean
public ChatModel resilientChatModel() {
    return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName(modelName)
            .maxRetries(3)  // Retry on transient failures
            .timeout(Duration.ofSeconds(60))
            .build();
}

@Service
public class FallbackRAGService {

    private final ChatModel primaryModel;
    private final ChatModel fallbackModel;

    public String generate(String prompt) {
        try {
            return primaryModel.chat(prompt);
        } catch (Exception e) {
            log.warn("Primary model failed, using fallback", e);
            return fallbackModel.chat(prompt);
        }
    }
}
```

### Model-Specific Parameters

```java
@Bean
public ChatModel customizedChatModel() {
    return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName("gpt-4-turbo")
            .temperature(0.7)
            .maxTokens(2000)        // Limit response length
            .topP(0.9)              // Nucleus sampling
            .frequencyPenalty(0.5)  // Reduce repetition
            .presencePenalty(0.3)   // Encourage topic diversity
            .build();
}
```

### Multiple Provider Support

```java
@Configuration
public class MultiProviderLLMConfig {

    @Bean
    public ChatModel openAiModel() {
        return OpenAiChatModel.builder()
            .apiKey(openAiApiKey)
            .modelName("gpt-4")
            .build();
    }

    @Bean
    public ChatModel anthropicModel() {
        return AnthropicChatModel.builder()
            .apiKey(anthropicApiKey)
            .modelName("claude-3-opus")
            .build();
    }

    @Bean
    public ChatModel azureModel() {
        return AzureOpenAiChatModel.builder()
            .endpoint(azureEndpoint)
            .apiKey(azureApiKey)
            .deploymentName("gpt-4")
            .build();
    }
}
```

## Testing

### Mock Models for Testing

```java
@TestConfiguration
public class TestLLMConfig {

    @Bean
    @Primary
    public ChatModel mockChatModel() {
        return new ChatModel() {
            @Override
            public String chat(String prompt) {
                return "Mock response for: " + prompt;
            }
        };
    }

    @Bean("validatorChatModel")
    public ChatModel mockValidatorModel() {
        return new ChatModel() {
            @Override
            public String chat(String prompt) {
                return "{\"safe\": true, \"violations\": [], \"confidence\": 1.0}";
            }
        };
    }
}
```

### Integration Tests

```java
@SpringBootTest
class LLMConfigIntegrationTest {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    @Qualifier("validatorChatModel")
    private ChatModel validatorChatModel;

    @Test
    void testPrimaryModelConfigured() {
        assertNotNull(chatModel);
        String response = chatModel.chat("Hello");
        assertNotNull(response);
        assertFalse(response.isBlank());
    }

    @Test
    void testValidatorModelConfigured() {
        assertNotNull(validatorChatModel);
        String response = validatorChatModel.chat("Is this safe?");
        assertNotNull(response);
    }

    @Test
    void testModelsAreDifferent() {
        assertNotSame(chatModel, validatorChatModel);
    }
}
```

## Security Considerations

### API Key Management

**Never hardcode**:
```java
// ❌ BAD
.apiKey("sk-proj-abc123...")
```

**Use environment variables**:
```java
// ✅ GOOD
@Value("${openai.api.key}")
private String openAiApiKey;
```

**Secret management** (production):
```java
@Configuration
public class SecretManagerConfig {

    @Bean
    public ChatModel secureModel(SecretsManager secretsManager) {
        String apiKey = secretsManager.getSecret("openai-api-key");
        return OpenAiChatModel.builder()
            .apiKey(apiKey)
            .build();
    }
}
```

### Rate Limiting

```java
@Bean
public ChatModel rateLimitedModel() {
    ChatModel baseModel = OpenAiChatModel.builder()
        .apiKey(openAiApiKey)
        .modelName(modelName)
        .build();

    return new RateLimitedChatModel(baseModel, 100, Duration.ofMinutes(1));
}

class RateLimitedChatModel implements ChatModel {
    private final RateLimiter limiter;
    private final ChatModel delegate;

    public String chat(String prompt) {
        if (!limiter.tryAcquire()) {
            throw new RateLimitException("Too many requests");
        }
        return delegate.chat(prompt);
    }
}
```

---

**Next Chapter**: [08 - Simple RAG Service](./08-simple-rag-service.md)

**Related Topics**:
- [Output Validator](./04-output-validator.md) - Uses validator model
- [Secure RAG Controller](./09-secure-rag-controller.md) - Uses primary model
