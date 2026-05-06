# Advanced RAG: Retrieval-Augmented Generation Patterns

Welcome to Module 02 of the LLM Spring Boot Workshop! This tutorial teaches you how to implement production-ready RAG (Retrieval-Augmented Generation) systems using advanced retrieval techniques.

## What You'll Build

A complete Advanced RAG pipeline featuring:

- **Query Transformation**: Multi-query generation and HyDE for better retrieval
- **Hybrid Search**: Combining vector search (semantic) with keyword search (lexical) using Reciprocal Rank Fusion
- **Re-Ranking**: Improving result quality with sophisticated scoring models
- **Grounded Generation**: Using LLMs to generate answers strictly based on retrieved context
- **Search Comparison**: Tools to evaluate different retrieval strategies

## Prerequisites

**REQUIRED:** You must complete **Module 01: Vectors & Embeddings** before starting this module.

This module assumes you understand:
- Vector embeddings and how text becomes numbers
- Similarity metrics (cosine similarity, Euclidean distance)
- Document chunking strategies
- Vector search fundamentals

You should also have:
- Java 17+ (Java 25 recommended)
- Maven 3.6+
- IDE (IntelliJ IDEA, VS Code, or Eclipse)
- LLM API access (OpenAI API key) OR local LLM server

## Technologies Used

- **Java 25** with preview features (records, pattern matching, structured concurrency)
- **Spring Boot 4.0.5** for application framework
- **LangChain4j 1.11.0** for AI integration
- **AllMiniLmL6V2** embedding model (384 dimensions)
- **OpenAI GPT-4** or compatible LLM for answer generation

## What You'll Learn

### RAG Techniques
- Multi-query generation to increase recall
- HyDE (Hypothetical Document Embeddings) for better query representation
- Hybrid search combining semantic and lexical matching
- Reciprocal Rank Fusion (RRF) for merging ranked lists
- Re-ranking with bi-encoders and cross-encoders
- Grounded generation to prevent hallucinations

### Advanced Concepts
- BM25 keyword search algorithm
- Query expansion strategies
- Context window management
- Pipeline orchestration
- Performance optimization with parallel retrieval

### Java & Spring Boot
- Structured concurrency (Java 21+)
- Service composition patterns
- REST API design with validation
- Structured logging for observability
- Error handling and graceful degradation

## Tutorial Structure

### Foundations (Chapters 1-3)
1. **Introduction** - RAG concepts and architecture
2. **RAGRequest/RAGResponse** - API contracts
3. **SearchComparisonResponse** - Comparing retrieval methods

### Query Processing (Chapter 4)
4. **QueryTransformer** - Multi-query and HyDE generation

### Retrieval Components (Chapters 5-8)
5. **KeywordSearchService** - BM25 lexical search
6. **ReRanker Interface** - Re-ranking abstraction
7. **EmbeddingBasedReRanker** - Re-ranking implementation
8. **HybridSearchService** - Combining vector + keyword with RRF

### Pipeline & API (Chapters 9-10)
9. **RAGService** - Complete pipeline orchestration
10. **RAGController** - REST endpoints

### Conclusion (Chapter 11)
11. **Conclusion** - Next steps and production considerations

## Getting Started

Follow the chapters in order to build your understanding from foundational concepts to the complete working RAG system.

**Estimated Time**: 4-5 hours

**Difficulty**: Advanced (requires Module 01 completion)

---

## Quick Start

```bash
# Clone the repository
git clone https://github.com/learnj-ai/learnj-workshops.git
cd learnj-workshops/llm-springboot/src/module-02-advanced-rag

# Build and run
mvn spring-boot:run

# Test the RAG endpoint
curl -X POST http://localhost:8080/api/v1/rag/query \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I reset my password?", "useQueryExpansion": true}'

# Compare search strategies
curl -X POST http://localhost:8080/api/v1/rag/compare \
  -H "Content-Type: application/json" \
  -d '{"query": "VPN setup", "topK": 5}'
```

---

Built with Spring Boot, LangChain4j, and Claude Code.
