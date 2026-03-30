# Architecting Intelligent Enterprise Systems: From Scratch

A hands-on workshop that transforms developers into AI architects using Spring Boot, Langchain4J, and Java 25. Students progress from embedding fundamentals through RAG pipelines, tool integration, autonomous agents, security hardening, and production deployment on OpenShift.

## Modules

| # | Module | Topic |
|---|--------|-------|
| 1 | Vectors & Embeddings | Embedding generation, distance metrics, chunking, vector store |
| 2 | Advanced RAG | Hybrid search, re-ranking, query transformation |
| 3 | Tools & MCP | Model Context Protocol, database and API tools |
| 4 | From Chatbots to Agents | ReAct pattern, memory, multi-agent orchestration |
| 5 | Security & Guardrails | Prompt injection defense, PII masking, output validation |
| 6 | Enterprise & Production | Evals, observability, caching, OpenShift deployment |

## Prerequisites

- Java 25
- Maven 3.9+
- Docker (or Podman)

## Building the Source Code

From this directory:

```bash
mvn clean install
```

To build a single module:

```bash
mvn clean install -pl src/module-01-vectors-embeddings
```

## Running the Workshop Site Locally

The workshop instructions are an [Antora](https://antora.org/) site using the RHDP Showroom theme. To build and preview it locally, run the following from the **repository root** (one level up from this directory):

```bash
cd ..

docker run --user=$(id -u) --rm \
  -v $(pwd):/showroom/repo \
  --mount 'type=tmpfs,dst=/showroom/repo/.cache,tmpfs-mode=1777' \
  --entrypoint antora \
  -w /showroom/repo \
  ghcr.io/rhpds/showroom-content:latest \
  default-site.yml
```

> **Note:** Replace `docker` with `podman` if that is your container runtime.

The generated site will be in the `output/` directory at the repository root. Open it in your browser:

```bash
open output/index.html
```

Or serve it locally:

```bash
npx http-server output -p 8443
```

Then visit http://localhost:8443.

### Rebuilding After Changes

There is no long-running dev server or file watcher. The container starts, builds the site, and exits. After editing any `.adoc` file, re-run the same `docker run` (or `podman run`) command above and refresh your browser. No containers need to be restarted -- each run is a clean, one-shot build that overwrites `output/`.

You can use fswatch to watch for changed for example

```bash

  # Install once
  brew install fswatch
  # Terminal 1 — serve
  npx http-server output -p 8443
  # Terminal 2 — watch and rebuild on save
  fswatch -o llm-springboot/site/content/ | while read; do
    docker run --user=$(id -u) --rm \
      -v $(pwd):/showroom/repo \
      --mount 'type=tmpfs,dst=/showroom/repo/.cache,tmpfs-mode=1777' \
      --entrypoint antora -w /showroom/repo \
      ghcr.io/rhpds/showroom-content:latest default-site.yml
  done

```

## Contributing

### Project Structure

```
llm-springboot/
├── pom.xml                        Parent POM (module declarations only)
├── README.md
├── .gitignore
│
├── site/                          Workshop instructions (Antora)
│   ├── www/                       Build output (git-ignored)
│   └── content/
│       ├── antora.yml             Component descriptor
│       ├── lib/                   Antora extensions
│       └── modules/ROOT/
│           ├── nav.adoc           Left-hand navigation
│           ├── assets/images/     Screenshots and diagrams
│           ├── examples/          Downloadable assets
│           ├── partials/          Reusable content fragments
│           └── pages/             AsciiDoc pages (one per module)
│
├── pm/                            Project management
│   ├── ADR.md                     Architecture Decision Records
│   ├── spec.md                    Technical specification
│   └── workshop-abstract.md       Workshop abstract and module overview
│
└── src/                           Source code (Maven modules)
    ├── module-01-vectors-embeddings/
    ├── module-02-advanced-rag/
    ├── module-03-tools-mcp/
    ├── module-04-chatbots-to-agents/
    ├── module-05-security-guardrails/
    └── module-06-enterprise-production/
```

**Three directories, three concerns:**

- **`site/`** — All Antora/AsciiDoc content for the workshop instructions. Edit pages under `site/content/modules/ROOT/pages/`. The GitHub Actions workflow at the repo root builds and deploys this to GitHub Pages.
- **`pm/`** — Planning and process documents. The ADR log tracks all architecture decisions. Update `ADR.md` when making significant technical choices.
- **`src/`** — Java source code organized as Maven modules, one per workshop module. Each module has its own `pom.xml` referencing the parent. Module naming follows `module-NN-slug` to match the corresponding `.adoc` page filename.

### Adding Content

- **New instruction page:** Create a `.adoc` file in `site/content/modules/ROOT/pages/` and add an `xref` entry to `site/content/modules/ROOT/nav.adoc`.
- **New image:** Place it in `site/content/modules/ROOT/assets/images/` and reference it with `image::filename.png[alt text]`.
- **New source code:** Add classes under the appropriate module in `src/module-NN-*/src/main/java/com/techcorp/assistant/`.
- **New decision:** Append an ADR entry to `pm/ADR.md`.
