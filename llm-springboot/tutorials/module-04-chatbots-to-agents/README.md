# From Chatbots to Autonomous Agents

Welcome to Module 04 of the LLM Spring Boot Workshop! This tutorial teaches you how to build autonomous AI agents that can reason, use tools, collaborate with other agents, and decompose complex tasks—going far beyond simple chatbot question-answering.

## What You'll Build

A multi-mode agent system demonstrating cutting-edge AI patterns:
- **ReAct Pattern**: Iterative reasoning with tool execution
- **Multi-Agent Architecture**: Specialized agents with intelligent routing
- **Conversation Memory**: Stateful sessions with Redis persistence
- **Task Decomposition**: Breaking complex problems into subtasks

## Technologies Used

- Java 17+
- Spring Boot 3.x
- LangChain4j
- OpenAI GPT-4
- Redis
- PostgreSQL
- Maven

## Getting Started

Follow the chapters in order to build your understanding from foundational patterns to complete autonomous agent systems.

**Estimated Time**: 4-5 hours

**Difficulty**: Advanced (requires understanding of Spring Boot and basic AI/LLM concepts)

## Prerequisites

- Completed Module 01 (Vectors & Embeddings) or equivalent vector search knowledge
- Understanding of Spring Boot, REST APIs, and dependency injection
- Familiarity with chatbot/LLM concepts
- OpenAI API key for GPT-4 access
- Redis and PostgreSQL installed or accessible

## Tutorial Structure

1. **Introduction**: Overview of agent architectures and the ReAct pattern
2. **ReAct Agent**: Implementing iterative reasoning with tools
3. **Specialized Agents**: Creating domain-focused agents
4. **Multi-Agent Orchestration**: Routing and collaborative patterns
5. **Conversation Memory**: Stateful sessions with Redis
6. **Task Decomposition**: Breaking down complex tasks
7. **Agent Tools**: Database and API integration
8. **Agent Controller**: REST API design
9. **Configuration**: Deployment and production setup
10. **Examples & Best Practices**: Real-world patterns and optimization

## Key Concepts

- **Autonomous Reasoning**: Agents that think through multi-step problems
- **Tool Execution**: Accessing databases, APIs, and external systems
- **Specialization**: Domain-focused agents for different areas
- **Orchestration**: Routing requests and synthesizing responses
- **Memory**: Maintaining context across conversation turns
- **Decomposition**: Breaking tasks into subtasks with dependencies

## Running the Examples

```bash
# Set environment variables
export OPENAI_API_KEY=your-api-key
export REDIS_HOST=localhost
export DB_URL=jdbc:postgresql://localhost:5432/techcorp

# Build and run
mvn clean install
mvn spring-boot:run

# Test the API
curl -X POST http://localhost:8084/api/v1/agent/execute \
  -H "Content-Type: application/json" \
  -d '{"message": "What is customer 12345?", "mode": "react"}'
```

## Additional Resources

- [LangChain4j Documentation](https://docs.langchain4j.dev/)
- [ReAct Paper](https://arxiv.org/abs/2210.03629)
- [OpenAI Function Calling Guide](https://platform.openai.com/docs/guides/function-calling)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)

---

Ready to build agents that can think, act, and collaborate? Let's begin!
