# Chapter: EmbeddingConfiguration - Setting Up the AI Brain

## Introduction: Choosing Your Translator

Imagine you're building a global translation service. Before you can translate anything, you need to decide: which translation engine will you use? Google Translate? DeepL? A custom model? This choice affects the quality, speed, and cost of every translation.

In vector search, **EmbeddingConfiguration** makes that same critical choice: which **embedding model** will convert text into numerical vectors? This isn't just a technical detail—it's the foundation that determines your search quality, speed, and resource requirements.

The embedding model is the "AI brain" that understands language meaning. Without it, you can't convert text to vectors. Without vectors, you can't search.

## How It Works: Spring Bean Configuration

```java
package com.techcorp.assistant.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {

    @Bean
    EmbeddingModel embeddingModel() {
        return new AllMiniLmL6V2EmbeddingModel();
    }
}
```

### Breaking It Down

**@Configuration**: Tells Spring Boot this class contains bean definitions—shared components that the framework will manage and inject where needed.

**@Bean**: Marks the `embeddingModel()` method as a factory that creates a singleton instance. Spring will call this method once at startup and reuse the same instance throughout the application.

**AllMiniLmL6V2EmbeddingModel**: This is our chosen embedding model. It's:
- **Local**: Runs on your machine, no API calls needed
- **Fast**: Small model (~30MB) optimized for speed
- **Free**: No usage costs or API keys
- **384-dimensional**: Each vector has 384 numbers

**proxyBeanMethods = false**: Performance optimization telling Spring not to create proxies for this configuration class.

## Code Deep Dive: Why This Model?

### The AllMiniLmL6V2 Model

This model is based on Microsoft's "all-MiniLM-L6-v2" from the sentence-transformers project. It's popular for good reasons:

| Feature | Value | Why It Matters |
|---------|-------|----------------|
| **Dimensions** | 384 | Smaller than BERT (768), faster to compute and compare |
| **Model Size** | ~30MB | Loads quickly, fits in memory easily |
| **Speed** | ~2000 sentences/sec on CPU | Fast enough for real-time search |
| **Quality** | Good for general text | Works well for documentation, FAQs, policies |
| **Runtime** | ONNX (optimized) | No Python or PyTorch required |

### How It's Used

Once configured, Spring Boot injects this bean into any service that needs it:

```java
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;  // Injected by Spring

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public Embedding generateEmbedding(String text) {
        Response<Embedding> response = embeddingModel.embed(text);
        return response.content();
    }
}
```

No need to manually create the embedding model—Spring handles it automatically thanks to our configuration.

### Alternative Embedding Models

You could swap in different models depending on your needs:

```java
// Option 1: Larger, more accurate model (but slower)
@Bean
EmbeddingModel embeddingModel() {
    return new BgeSmallEnEmbeddingModel();  // 384-dim, better quality
}

// Option 2: Use OpenAI's embeddings (requires API key and costs money)
@Bean
EmbeddingModel embeddingModel() {
    return OpenAiEmbeddingModel.builder()
        .apiKey(System.getenv("OPENAI_API_KEY"))
        .modelName("text-embedding-3-small")
        .build();
}

// Option 3: Use a multilingual model for non-English text
@Bean
EmbeddingModel embeddingModel() {
    return new MultilingualE5SmallEmbeddingModel();
}
```

The beauty of Spring's dependency injection is that you can change the model in one place (this configuration class) and the entire application automatically uses the new model.

## Relationships: Configuration Powers Services

### Who Depends on EmbeddingConfiguration

- **EmbeddingService**: The primary consumer. It receives the `EmbeddingModel` bean and uses it to convert text to vectors.

### What EmbeddingConfiguration Depends On

- **LangChain4j library**: Provides the `EmbeddingModel` interface and implementations like `AllMiniLmL6V2EmbeddingModel`.
- **ONNX Runtime** (transitive dependency): The underlying engine that runs the machine learning model.

### The Configuration Flow

```
Application Startup
    ↓
[Spring Boot scans for @Configuration classes]
    ↓
[Finds EmbeddingConfiguration]
    ↓
[Calls embeddingModel() method once]
    ↓
[Creates AllMiniLmL6V2EmbeddingModel instance]
    ↓
[Stores in application context as singleton]
    ↓
[Injects into EmbeddingService constructor]
    ↓
[EmbeddingService ready to generate embeddings]
```

## Key Takeaways

- **EmbeddingConfiguration centralizes the choice of embedding model** using Spring's @Configuration and @Bean annotations
- **The AllMiniLmL6V2 model is ideal for learning**: local, fast, free, and good quality for general text
- **Spring Boot manages the lifecycle**: creates one instance at startup and injects it everywhere it's needed
- **Swapping models is easy**: change one line in the configuration and the entire application adapts
- **This configuration is foundational**: no embedding model means no vector search

## Next Steps

Now that you understand how the embedding model is configured, you're ready to explore how documents are prepared for embedding. In the next chapter, **ChunkingStrategy**, you'll learn why we split large documents into smaller chunks and the different strategies for doing so effectively.
