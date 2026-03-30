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
- GitHub Actions workflow (`site/.github/workflows/deploy-pages.yml`) deploys to GitHub Pages automatically on push to `main`.
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
