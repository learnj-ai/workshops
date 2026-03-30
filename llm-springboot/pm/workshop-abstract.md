# Architecting Intelligent Enterprise Systems: From scratch

Moving beyond simple chatbots requires a deep understanding of how to architect secure, scalable, and autonomous AI systems. This hands-on workshop transforms developers into AI architects. We will start by deconstructing the mechanics of Vectors and Embeddings, move into building RAG pipelines, and then evolve our systems into autonomous Agents using the Model Context Protocol (MCP).

Crucially, we will cover the "Day 2" operations of AI: implementing Security Guardrails to prevent prompt injection and data leakage, and adhering to Enterprise Best Practices for production deployment.

- Module 1: The Physics of AI – Vectors & Embeddings
- Module 2: Advanced RAG (Retrieval-Augmented Generation)
- Module 3: Tools & The Model Context Protocol (MCP)
- Module 4: From Chatbots to Agents
- Module 5: Security & Guardrails
- Module 6: Enterprise Best Practices & Production


### Workshop: AI Architect Foundations

---

## Table of Contents

1. [Module 1: The Physics of AI – Vectors & Embeddings](https://www.google.com/search?q=%23module-1)
2. [Module 2: Advanced RAG (Retrieval-Augmented Generation)](https://www.google.com/search?q=%23module-2)
3. [Module 3: Tools & The Model Context Protocol (MCP)](https://www.google.com/search?q=%23module-3)
4. [Module 4: From Chatbots to Agents](https://www.google.com/search?q=%23module-4)
5. [Module 5: Security & Guardrails](https://www.google.com/search?q=%23module-5)
6. [Module 6: Enterprise Best Practices & Production](https://www.google.com/search?q=%23module-6)

---

<a name="module-1"></a>

### Module 1: The Physics of AI – Vectors & Embeddings

**Context:** Before building, you must understand the "material science" of AI. We move beyond treating embeddings as black boxes and look at how data geometry impacts model performance.

* **Dimensionality & Latent Space:** Visualizing how models "see" relationships.
* **Distance Metrics:** Choosing between Cosine Similarity, Euclidean, and Inner Product.
* **Chunking Strategies:** Semantic vs. Recursive Character splitting.

**Learning Objectives:**

* **Identify** which distance metric ($Cosine$, $L2$, or $Dot Product$) is optimal for specific data types.
* **Construct** a data-pre-processing pipeline that utilizes semantic chunking to preserve context.
* **Evaluate** the trade-offs between different embedding models in terms of latency vs. accuracy.

---

<a name="module-2"></a>

### Module 2: Advanced RAG (Retrieval-Augmented Generation)

**Context:** Simple RAG is easy; production RAG is hard. This module covers the "Retrieval Gap" and how to bridge it using sophisticated search patterns.

* **Hybrid Search:** Combining Keyword (BM25) with Vector search.
* **Re-ranking:** Using Cross-Encoders to refine initial retrieval sets.
* **Query Transformation:** Implementing Multi-query and Hypothetical Document Embeddings (HyDE).

**Learning Objectives:**

* **Implement** a Two-Stage Retrieval system using Vector search followed by a Cross-Encoder Re-ranker.
* **Design** a Hybrid Search architecture that merges semantic meaning with exact keyword matching.
* **Apply** Query Expansion techniques to improve recall for short or ambiguous user prompts.

---

<a name="module-3"></a>

### Module 3: Tools & The Model Context Protocol (MCP)

**Context:** The breakthrough in AI utility comes from connecting models to your data. We explore MCP as the new standard for local and remote tool integration.

* **The MCP Ecosystem:** Understanding Host, Client, and Server relationships.
* **Resource Management:** Safely exposing databases and APIs to an LLM.
* **Standardizing Toolkits:** Building reusable, protocol-compliant connectors.

**Learning Objectives:**

* **Configure** an MCP Server to expose local or cloud-based data sources to an AI client.
* **Develop** custom tools that follow the Model Context Protocol for standardized integration.
* **Manage** tool "discoverability" to ensure the model selects the correct function for a given task.

---

<a name="module-4"></a>

### Module 4: From Chatbots to Agents

**Context:** An Agent is a system that can reason, use tools, and self-correct. We transition from linear pipelines to iterative loops.

* **Reasoning Frameworks:** Implementing ReAct (Reason + Act) and Chain-of-Thought patterns.
* **State Management:** Handling short-term memory and persistent session history.
* **Agentic Workflows:** Orchestrating multi-agent handoffs and task decomposition.

**Learning Objectives:**

* **Build** an agentic loop using the **ReAct** pattern.
* **Architect** stateful memory systems that allow agents to track progress across long-running tasks.
* **Orchestrate** multi-agent handoffs where specialized agents collaborate on a single objective.

---

<a name="module-5"></a>

### Module 5: Security & Guardrails

**Context:** AI introduces a new attack surface. We focus on hardening the system against both accidental and malicious misuse.

* **Prompt Injection Defense:** Separating system instructions from untrusted user input.
* **Data Leakage Prevention:** Implementing PII masking and access controls for RAG.
* **Validation Layers:** Using tools like NeMo Guardrails or Llama Guard to filter toxic outputs.

**Learning Objectives:**

* **Defend** against Prompt Injection attacks using delimited system prompts and input sanitization.
* **Establish** automated PII masking within the RAG pipeline.
* **Deploy** an LLM-based "Judge" or Guardrail layer to validate output safety and brand alignment.

---

<a name="module-6"></a>

### Module 6: Enterprise Best Practices & Production

**Context:** The final step is moving from a laptop to the cloud. We cover the operational rigor required for "Day 2" AI.

* **LLM Evaluation (Evals):** Creating automated benchmarks to measure accuracy.
* **Observability & Tracing:** Monitoring latency, token costs, and decision-pathways.
* **Deployment Patterns:** Optimization for cold-start latency and model versioning.

**Learning Objectives:**

* **Create** an "Evaluation Gold Set" to provide deterministic benchmarks for non-deterministic models.
* **Implement** distributed tracing to debug why an agent made a specific decision.
* **Optimize** token consumption and latency through caching and model distillation strategies.

---



Technology stack to use: 
Spring boot
Langchain4J
Java 25
Docker
OpenShift
Module instructions written in AsciiDoc using Antora with the RHDP Showroom theme.
Template: https://github.com/rhpds/mta8-workshop

