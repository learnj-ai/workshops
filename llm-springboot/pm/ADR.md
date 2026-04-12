# Architecture Decision Records

This document tracks all architecture and design decisions for the **Architecting Intelligent Enterprise Systems: From Scratch** workshop.

---

## ADR-001: Unified Demo Project Across All Modules

**Date:** 2026-03-30
**Status:** Accepted

### Context

The workshop has six modules covering different AI/LLM topics. We needed to decide whether each module would use a standalone project or a single evolving codebase.

### Decision

Use a single unified demo project — the **Enterprise Knowledge Assistant** for a fictional company "TechCorp" — that evolves progressively through all six modules.

### Consequences

- Students build on prior work, reinforcing learning.
- Each module adds capabilities to the same codebase (vectors → RAG → tools → agents → security → production).
- Requires careful sequencing so each module's starter code is the previous module's output.

---

## ADR-002: Technology Stack — Spring Boot, Langchain4J, Java 25

**Date:** 2026-03-30
**Status:** Accepted

### Context

The workshop targets enterprise Java developers building AI-powered applications. We needed a framework and LLM library that align with enterprise adoption patterns.

### Decision

- **Spring Boot 3.4** as the application framework.
- **Langchain4J 1.11** as the LLM integration library.
- **Java 25** as the language version.

### Consequences

- Leverages familiar Spring ecosystem (DI, web, data, actuator).
- Langchain4J provides native Java abstractions for embeddings, chat models, tools, and memory — no Python bridge needed.
- Java 25 enables modern language features (records, text blocks, pattern matching).

---

## ADR-003: Chroma as the Vector Store

**Date:** 2026-03-30
**Status:** Accepted

### Context

We needed a vector database for the embedding and RAG modules that is easy to run locally and has Langchain4J support.

### Decision

Use **ChromaDB** as the vector store, running via Docker Compose for local development.

### Consequences

- Lightweight, easy to spin up with `docker compose up -d chroma`.
- First-class Langchain4J integration via `langchain4j-chroma`.
- Not a production-grade choice for large-scale deployments, but ideal for workshop learning.

---

## ADR-004: Docker Compose for Local Development Services

**Date:** 2026-03-30
**Status:** Accepted

### Context

The workshop requires several infrastructure services (vector DB, relational DB, cache, monitoring). Students need a simple way to run them locally.

### Decision

Provide a `docker-compose.yml` with all required services: Chroma, PostgreSQL (with pgvector), Redis, Prometheus, and Grafana.

### Consequences

- Single `docker compose up -d` starts all dependencies.
- Students don't need to install services natively.
- Requires Docker Desktop or Podman on student machines.

---

## ADR-005: OpenShift as the Production Deployment Target

**Date:** 2026-03-30
**Status:** Accepted

### Context

Module 6 covers production deployment. We needed a Kubernetes platform that reflects enterprise reality and integrates with the RHDP workshop environment.

### Decision

Use **OpenShift** as the deployment target, with UBI-based container images and OpenShift Routes for ingress.

### Consequences

- Aligns with Red Hat ecosystem and RHDP showroom environment.
- Students learn OpenShift-specific concepts (Routes, `oc` CLI) alongside Kubernetes fundamentals.
- Deployment manifests include Deployment, Service, and Route resources.

---

## ADR-006: Antora with RHDP Showroom Instead of GitBook

**Date:** 2026-03-30
**Status:** Accepted
**Supersedes:** Original plan to use GitBook (referenced in spec.md)

### Context

The initial spec called for GitBook to write and publish workshop lab instructions. However, the workshop will be delivered through the **Red Hat Demo Platform (RHDP)** showroom, which provides an integrated OpenShift-based environment for running hands-on workshops. RHDP showroom uses Antora with AsciiDoc and a dedicated showroom UI theme.

### Decision

Use **Antora** with **AsciiDoc** for all workshop instructions, styled with the **RHDP Showroom theme** (`rhdp_showroom_theme`). Follow the conventions from the [mta8-workshop](https://github.com/rhpds/mta8-workshop) reference implementation.

### Consequences

- Workshop content is authored in `.adoc` files under `site/content/modules/ROOT/pages/`.
- Navigation is defined in `site/content/modules/ROOT/nav.adoc`.
- Site is built using the `ghcr.io/rhpds/showroom-content` container image.
- GitHub Actions workflow (`.github/workflows/deploy-pages.yml` at repo root) deploys to GitHub Pages automatically on push to `main` when `llm-springboot/site/**` changes.
- Native integration with RHDP showroom — supports variable substitution (e.g., `{lab_name}`, cluster URLs) for per-environment customization.
- AsciiDoc is richer than Markdown for technical content (admonitions, includes, conditional content, tabs).
- GitBook is no longer part of the toolchain.

---

## ADR-007: Workshop Module Structure — 6 Progressive Modules

**Date:** 2026-03-30
**Status:** Accepted

### Context

We needed to define the scope and progression of the workshop content.

### Decision

Six modules, each building on the previous:

1. **Vectors & Embeddings** — Fundamentals of embedding generation, distance metrics, chunking.
2. **Advanced RAG** — Hybrid search, re-ranking, query transformation.
3. **Tools & MCP** — Model Context Protocol, database and API tools.
4. **From Chatbots to Agents** — ReAct pattern, memory, multi-agent orchestration.
5. **Security & Guardrails** — Prompt injection defense, PII masking, output validation.
6. **Enterprise Best Practices & Production** — Evals, observability, caching, OpenShift deployment.

### Consequences

- Clear learning progression from foundations to production.
- Each module targets ~45 minutes of hands-on work (Module 6: ~60 minutes).
- Each module includes: goals, background theory, hands-on labs, challenge exercises, knowledge checks, and a recap.

---

## ADR-008: Project Directory Structure — site / pm / src

**Date:** 2026-03-30
**Status:** Accepted

### Context

As the workshop project grew beyond just documentation, we needed a clear separation between workshop instructions, project management documents, and source code. The original flat layout mixed Antora content, markdown planning docs, and code artifacts at the same level.

### Decision

Adopt a three-directory structure under each workshop (e.g., `llm-springboot/`):

- **`site/`** — Antora site (workshop instructions). Contains `default-site.yml`, `content/`, `.github/workflows/`, and all AsciiDoc pages.
- **`pm/`** — Project management documents. Contains `spec.md`, `workshop-abstract.md`, `ADR.md`, and any other planning/process markdown files.
- **`src/`** — All source code. Contains Maven modules, one per workshop module.

A top-level `pom.xml` at the workshop root (`llm-springboot/pom.xml`) acts as the parent POM and references modules only.

### Consequences

- Clear separation of concerns: instructions, planning, and code never intermingle.
- Each concern can evolve independently (e.g., site can be rebuilt without touching source code).
- The `site/` directory is self-contained for Antora builds — it has its own `.github/workflows/` for GitHub Pages deployment.
- The `pm/` directory is the single place to look for all project decisions and planning artifacts.
- New workshops follow the same convention: `workshops/<name>/{site,pm,src}`.

---

## ADR-009: Multi-Module Maven Project — One Module Per Workshop Module

**Date:** 2026-03-30
**Status:** Accepted

### Context

The workshop has six progressive modules. We needed to decide how to organize the Java source code: a single monolithic project, or one Maven module per workshop module.

### Decision

Use a **multi-module Maven project**. The parent POM (`llm-springboot/pom.xml`) declares `<packaging>pom</packaging>` and lists six `<module>` entries under `src/`:

```
src/module-01-vectors-embeddings/
src/module-02-advanced-rag/
src/module-03-tools-mcp/
src/module-04-chatbots-to-agents/
src/module-05-security-guardrails/
src/module-06-enterprise-production/
```

Each module has its own `pom.xml` with a `<parent>` reference back to the workshop root POM.

### Consequences

- Students can build and run individual modules independently.
- Dependencies are scoped per module — Module 1 doesn't pull in security or monitoring libraries.
- Later modules can depend on earlier ones if code reuse is needed.
- `mvn install` from the workshop root builds everything; `mvn install -pl src/module-01-vectors-embeddings` builds just one.
- Module naming follows a `module-NN-slug` convention matching the Antora page filenames for traceability.

---

## ADR-010: Module 01 — In-Process Embeddings and In-Memory Vector Store

**Date:** 2026-04-12
**Status:** Accepted

### Context

Module 01 (Vectors & Embeddings) is the first hands-on module. Students need to understand embeddings, distance metrics, chunking, and vector search before introducing external infrastructure like ChromaDB. We needed to decide whether Module 01 should start with a full vector database or keep dependencies minimal so students focus on the concepts.

### Decision

Module 01 uses:

- **In-process embedding model** (`langchain4j-embeddings-all-minilm-l6-v2`) — runs entirely inside the JVM, no external API calls or GPU required.
- **In-memory vector store** — a custom `VectorStoreService` that indexes document segments and performs brute-force nearest-neighbor search at query time.
- **Three similarity metrics** (cosine, dot product, Euclidean) implemented from scratch in `SimilarityCalculator`, so students see the math before relying on library abstractions.
- **A pluggable chunking layer** (`DocumentChunker` with `ChunkingStrategy`) to demonstrate how chunk size and overlap affect retrieval quality.
- **Sample knowledge-base documents** (`password-reset.md`, `api-rate-limits.md`, `vpn-access.md`) representing realistic enterprise support content.
- **A REST API** (`VectorSearchController`) exposing `/api/search` so students can experiment with queries interactively.

ChromaDB (ADR-003) is deferred to Module 02, where it replaces the in-memory store and introduces persistent, scalable vector storage.

### Consequences

- Zero external dependencies for Module 01 — students run `mvn spring-boot:run` and immediately have a working semantic search service.
- Students learn what a vector store *does* before using one as a black box.
- The in-memory store is intentionally naive (linear scan) to motivate why production systems use ANN indexes — a teaching point for Module 02.
- The MiniLM model (~80 MB) is bundled as a Maven dependency and downloads once; no API keys needed.
- Module 01's `VectorStoreService` and `SimilarityCalculator` become throwaway code once ChromaDB is introduced, which is acceptable — the goal is understanding, not reuse.

---

## ADR-011: Upgrade to Spring Boot 4.0.5 for Java 25 Support

**Date:** 2026-04-12
**Status:** Accepted
**Supersedes:** ADR-002 (technology stack — Spring Boot version only)

### Context

ADR-002 specified Spring Boot 3.4 as the application framework. However, Spring Boot 3.4.0 bundles Spring Framework 6.2.0, whose embedded ASM library only supports class files up to Java 23 (major version 67). Compiling with Java 25 (`--enable-preview`) produces class files with major version 69, causing `ClassFormatException: Unsupported class file major version 69` at startup.

Java 25 preview features — particularly `StructuredTaskScope` for structured concurrency — are valuable teaching tools for the workshop.

### Decision

Upgrade the parent POM from **Spring Boot 3.4.0 → 4.0.5**, which ships **Spring Framework 7.0.6** with ASM support for Java 25 class files. Set `<java.version>25</java.version>` in the parent POM and enable `--enable-preview` globally via `maven-compiler-plugin` and `maven-surefire-plugin`.

### Consequences

- All modules compile and run on Java 25 with preview features enabled.
- `StructuredTaskScope` can be used for structured concurrency (e.g., parallel search comparison in `RAGController`).
- Spring Framework 7.0 introduces API changes that required test updates:
  - `MockHttpServletRequestBuilder.contentType()` now takes `String` instead of `MediaType` — use `MediaType.APPLICATION_JSON_VALUE`.
  - `@AutoConfigureMockMvc` was removed — controller tests use `MockMvcBuilders.standaloneSetup()` instead.
  - With multiple constructors, Spring no longer auto-selects the public one — `@Autowired` is required to disambiguate.
- ADR-002's technology stack is now: Spring Boot 4.0.5, LangChain4J 1.11, Java 25.

---

## ADR-012: Module 02 — Hybrid RAG Pipeline with BM25, RRF, and LLM-Powered Query Expansion

**Date:** 2026-04-12
**Status:** Accepted

### Context

Module 02 (Advanced RAG) builds on Module 01's vector search to teach students why naive vector-only retrieval is insufficient for production use. Students need to see:
1. Where keyword search outperforms vector search (exact terms like "SEV1", "HTTP 429").
2. How to combine both retrieval methods without losing results from either.
3. How query transformation techniques improve recall.
4. How the full RAG pipeline ties retrieval to LLM-generated answers.

We needed to decide the retrieval architecture, which LLM provider to use, and how to introduce the first external API dependency.

### Decision

Module 02 implements a complete RAG pipeline with the following components:

- **BM25/TF-IDF keyword search** (`KeywordSearchService`) — implemented from scratch so students see term frequency, inverse document frequency, and length normalization before using a library.
- **Reciprocal Rank Fusion (RRF)** in `HybridSearchService` — merges vector and keyword ranked lists with a constant k=60, producing a single ranked list without needing score normalization.
- **Embedding-based re-ranker** (`EmbeddingBasedReRanker` implementing `ReRanker` interface) — re-ranks merged candidates using cosine similarity. This is a bi-encoder approach; the `ReRanker` interface is designed so a cross-encoder (e.g., ms-marco-MiniLM) can be plugged in later.
- **Query transformation** (`QueryTransformer`) — two techniques using `ChatModel`:
  - *Multi-query*: generates 3 alternative phrasings to increase recall.
  - *HyDE (Hypothetical Document Embeddings)*: generates a hypothetical answer document, then uses its embedding for vector-only retrieval.
- **RAG pipeline** (`RAGService`) — orchestrates the full flow: query expansion → hybrid retrieval per query variant → deduplication → context assembly → LLM answer generation.
- **Search comparison endpoint** (`POST /api/v1/rag/compare`) — returns vector-only, keyword-only, and hybrid results side-by-side so students can compare retrieval quality. Uses `StructuredTaskScope` for parallel execution (Java 25 preview).
- **OpenAI as the LLM provider** (`langchain4j-open-ai`) — configured via `OPENAI_API_KEY` environment variable. This is the first module requiring an external API key.
- **Expanded knowledge base** — 7 documents (3 from Module 01 + 4 new: employee onboarding, incident response, database access, deployment process) to provide enough content for meaningful retrieval comparisons.
- **Structured pipeline logging** — each RAG stage logs timing, query alternatives, retrieval counts, segment previews, and context size so students can observe the pipeline behavior in real time.

### Consequences

- Module 02 requires an OpenAI API key (set via `OPENAI_API_KEY` env var) — this is the first external dependency beyond the JVM. Students who don't have a key can still use the `/api/v1/rag/compare` endpoint to study retrieval without LLM generation.
- The BM25 implementation is educational, not production-grade (no stop-word removal, no stemming). This is intentional — students learn the algorithm before using Lucene/Elasticsearch.
- The `ReRanker` interface allows swapping in a cross-encoder model in later modules without changing the pipeline.
- The `/compare` endpoint is a powerful teaching tool — students can craft queries where keyword search wins, vector search wins, or hybrid search wins, building intuition for when to use each.
- Module 01's foundation code (embeddings, chunking, similarity, vector store) is copied into Module 02 rather than referenced as a Maven dependency, keeping each module self-contained and independently runnable.
