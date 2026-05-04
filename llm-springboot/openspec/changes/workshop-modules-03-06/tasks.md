## 1. Module 03 - Tools & MCP Setup

- [ ] 1.1 Create Maven module `src/module-03-tools-mcp/` with pom.xml
- [ ] 1.2 Add dependencies: spring-boot-starter-web, spring-boot-starter-data-jdbc, postgresql, langchain4j, langchain4j-open-ai
- [ ] 1.3 Create PostgreSQL schema for customers and tickets tables
- [ ] 1.4 Add sample data SQL script for customers and support tickets
- [ ] 1.5 Update docker-compose.yml with PostgreSQL init script

## 2. Module 03 - Tool Implementation

- [ ] 2.1 Implement CustomerDataTool with @Tool annotation for getCustomerInfo method
- [ ] 2.2 Implement CustomerDataTool searchTickets method with status parameter
- [ ] 2.3 Create WeatherTool with @Tool annotation for getCurrentWeather method
- [ ] 2.4 Add error handling for non-existent customers and API failures
- [ ] 2.5 Create MCPServerConfig with ChatLanguageModel bean registering all tools

## 3. Module 03 - Tool Orchestration

- [ ] 3.1 Implement ToolOrchestrator service handling tool execution requests
- [ ] 3.2 Add logic to detect tool execution requests in AI responses
- [ ] 3.3 Implement tool result processing and final response generation
- [ ] 3.4 Create AssistantController with POST /api/v1/assistant/chat endpoint
- [ ] 3.5 Add request/response DTOs (ChatRequest, ChatResponse)

## 4. Module 03 - Tests

- [ ] 4.1 Write unit tests for CustomerDataTool methods
- [ ] 4.2 Write unit tests for WeatherTool with mocked API calls
- [ ] 4.3 Write integration test for tool orchestration flow
- [ ] 4.4 Write controller test for /api/v1/assistant/chat endpoint

## 5. Module 04 - Agents Setup

- [ ] 5.1 Create Maven module `src/module-04-chatbots-to-agents/` with pom.xml
- [ ] 5.2 Add dependencies: spring-boot-starter-web, spring-boot-starter-data-redis, langchain4j, langchain4j-open-ai
- [ ] 5.3 Copy base services from Module 03 (tools, embeddings)
- [ ] 5.4 Configure Redis connection in application.properties

## 6. Module 04 - ReAct Agent Implementation

- [ ] 6.1 Create ReActAgent service with solve method and max iterations parameter
- [ ] 6.2 Implement REACT_PROMPT template with THOUGHT/ACTION/OBSERVATION structure
- [ ] 6.3 Add extractAction method parsing ACTION: pattern from LLM response
- [ ] 6.4 Add extractFinalAnswer method parsing FINAL ANSWER: pattern
- [ ] 6.5 Implement iterative loop executing thought-action-observation cycle
- [ ] 6.6 Add logging for each iteration showing thought, action, observation

## 7. Module 04 - Conversation Memory

- [ ] 7.1 Create ConversationMemoryService managing session memories
- [ ] 7.2 Implement RedisChatMemoryStore implementing ChatMemoryStore interface
- [ ] 7.3 Add getOrCreateMemory method creating MessageWindowChatMemory with session ID
- [ ] 7.4 Implement Redis TTL (24 hours) for memory expiration
- [ ] 7.5 Add methods: addMessage, getHistory, clearMemory

## 8. Module 04 - Multi-Agent Orchestration

- [ ] 8.1 Create SpecializedAgent interface with process method
- [ ] 8.2 Implement CustomerSupportAgent handling support queries
- [ ] 8.3 Implement TechnicalDocAgent handling documentation queries
- [ ] 8.4 Implement ProductExpertAgent handling product queries
- [ ] 8.5 Create MultiAgentOrchestrator with routing logic
- [ ] 8.6 Implement LLM-based routing prompt selecting appropriate agent
- [ ] 8.7 Add collaborativeRequest method gathering all agent perspectives
- [ ] 8.8 Implement response synthesis combining multi-agent outputs

## 9. Module 04 - Task Decomposition

- [ ] 9.1 Create TaskDecomposer service with executeComplexTask method
- [ ] 9.2 Implement decompose method generating subtasks via LLM
- [ ] 9.3 Add JSON parsing for subtask structure (id, description, dependencies)
- [ ] 9.4 Implement dependency-aware execution order
- [ ] 9.5 Create synthesizeResults method combining subtask outputs
- [ ] 9.6 Define Subtask, SubtaskResult, TaskExecutionResult records

## 10. Module 04 - Agent API & Tests

- [ ] 10.1 Create AgentController with POST /api/v1/agent/execute endpoint
- [ ] 10.2 Add session management loading/saving conversation history
- [ ] 10.3 Create AgentRequest and AgentResponse DTOs
- [ ] 10.4 Write unit tests for ReActAgent iterations
- [ ] 10.5 Write integration tests for memory persistence with Redis
- [ ] 10.6 Write tests for multi-agent routing scenarios
- [ ] 10.7 Write tests for task decomposition with dependencies

## 11. Module 05 - Security Setup

- [ ] 11.1 Create Maven module `src/module-05-security-guardrails/` with pom.xml
- [ ] 11.2 Add dependencies: spring-boot-starter-web, spring-boot-starter-data-redis, spring-boot-starter-security, langchain4j
- [ ] 11.3 Copy RAG services from Module 02 (vector search, hybrid search, RAG pipeline)
- [ ] 11.4 Configure security logging to application.yml

## 12. Module 05 - Prompt Injection Defense

- [ ] 12.1 Create PromptInjectionGuard component with validate method
- [ ] 12.2 Add regex patterns for common injection attempts (ignore instructions, system override, role manipulation)
- [ ] 12.3 Implement special character ratio check (>30% threshold)
- [ ] 12.4 Create sanitizeInput method removing HTML/XML tags
- [ ] 12.5 Add whitespace normalization to sanitization
- [ ] 12.6 Define ValidationResult record with approved/rejected states

## 13. Module 05 - PII Masking

- [ ] 13.1 Create PIIMaskingService with maskPII method
- [ ] 13.2 Add regex patterns for email, phone, SSN, credit card
- [ ] 13.3 Implement masking replacing matches with [TYPE_REDACTED] tokens
- [ ] 13.4 Create detectPII method returning PIIMatch list
- [ ] 13.5 Define PIIMatch and PIIDetectionResult records

## 14. Module 05 - Output Validation

- [ ] 14.1 Create OutputValidator service with LLM-as-judge
- [ ] 14.2 Configure separate ChatLanguageModel (gpt-3.5-turbo, temperature=0.0)
- [ ] 14.3 Implement validateOutput method checking toxicity, confidentiality, tone
- [ ] 14.4 Add JSON parsing for validation response (safe, violations, confidence)
- [ ] 14.5 Implement containsHallucination method comparing output to sources
- [ ] 14.6 Define ValidationCriteria record

## 15. Module 05 - Access Control & Audit

- [ ] 15.1 Create DocumentAccessControl service with filterByPermissions method
- [ ] 15.2 Implement hasAccess checking required_role and department metadata
- [ ] 15.3 Add enrichWithACL method adding role/department to segment metadata
- [ ] 15.4 Create SecurityAuditService with logSecurityEvent method
- [ ] 15.5 Implement Redis storage for audit events
- [ ] 15.6 Add severity-based alerting (HIGH/CRITICAL trigger ERROR logs)
- [ ] 15.7 Define SecurityEvent, AuditEvent records and Severity enum

## 16. Module 05 - Secure RAG API & Tests

- [ ] 16.1 Create SecureRAGController with POST /api/v1/secure/query endpoint
- [ ] 16.2 Wire security layers in sequence: injection guard → sanitize → PII mask → RAG → validate → PII mask
- [ ] 16.3 Add security audit logging at each layer
- [ ] 16.4 Create SecureRequest and SecureResponse DTOs
- [ ] 16.5 Write unit tests for each security layer independently
- [ ] 16.6 Write integration tests for full secure pipeline
- [ ] 16.7 Write tests for prompt injection scenarios
- [ ] 16.8 Write tests for PII detection/masking accuracy

## 17. Module 06 - Production Setup

- [ ] 17.1 Create Maven module `src/module-06-enterprise-production/` with pom.xml
- [ ] 17.2 Add dependencies: spring-boot-starter-actuator, micrometer-registry-prometheus, opentelemetry-api, spring-boot-starter-aop
- [ ] 17.3 Copy RAG services and security layers from Modules 02 and 05
- [ ] 17.4 Create sample eval dataset `data/eval-golden-set.json` with 10+ test cases
- [ ] 17.5 Update docker-compose.yml adding Prometheus and Grafana services

## 18. Module 06 - Evaluation Framework

- [ ] 18.1 Create EvaluationService with runEvaluation method
- [ ] 18.2 Implement loadEvalSet parsing JSON to EvalCase objects
- [ ] 18.3 Add calculateAccuracy using embedding cosine similarity
- [ ] 18.4 Add calculateRelevance comparing response to context embeddings
- [ ] 18.5 Add calculateFaithfulness using LLM-as-judge (0.0-1.0 score)
- [ ] 18.6 Implement calculateAverages aggregating metrics across all cases
- [ ] 18.7 Define EvalCase, EvalResult, MetricAverages, EvaluationReport records

## 19. Module 06 - Distributed Tracing

- [ ] 19.1 Create TracingConfig bean configuring OpenTelemetry Tracer
- [ ] 19.2 Create @Traced annotation for method instrumentation
- [ ] 19.3 Implement RAGTracingAspect with @Around advice
- [ ] 19.4 Add span creation with method name and component attribute
- [ ] 19.5 Implement exception recording in spans (status=ERROR)
- [ ] 19.6 Add span.makeCurrent() for context propagation

## 20. Module 06 - Metrics & Monitoring

- [ ] 20.1 Create MetricsCollector service with MeterRegistry
- [ ] 20.2 Add counter "rag.queries.total" for query count
- [ ] 20.3 Add timer "rag.response.time" for latency tracking
- [ ] 20.4 Add gauge "rag.tokens.used" for token consumption
- [ ] 20.5 Instrument RAGService with metric recording
- [ ] 20.6 Create Prometheus scrape config targeting /actuator/prometheus
- [ ] 20.7 Create Grafana dashboard JSON with query rate, latency, error panels

## 21. Module 06 - Caching & Optimization

- [ ] 21.1 Create CachingService with exact match cache using @Cacheable
- [ ] 21.2 Implement semanticCacheGet checking embedding similarity (threshold 0.95)
- [ ] 21.3 Implement semanticCachePut storing query-response pairs in Redis
- [ ] 21.4 Add embedding cache (in-memory Map) to avoid recomputing embeddings
- [ ] 21.5 Create TokenOptimizer service with optimizeContext method
- [ ] 21.6 Implement segment selection by token budget and relevance score
- [ ] 21.7 Add compressPrompt removing redundant whitespace and filler words
- [ ] 21.8 Define TokenizedSegment record

## 22. Module 06 - OpenShift Deployment

- [ ] 22.1 Create Dockerfile using registry.access.redhat.com/ubi9/openjdk-21 base
- [ ] 22.2 Add JAR copy and ENTRYPOINT instructions
- [ ] 22.3 Create deployment.yaml with Deployment resource (3 replicas)
- [ ] 22.4 Add resource requests (1Gi memory, 500m CPU) and limits (2Gi, 1000m)
- [ ] 22.5 Configure liveness probe on /actuator/health (60s initial delay)
- [ ] 22.6 Configure readiness probe on /actuator/health/readiness (30s initial delay)
- [ ] 22.7 Create Service resource exposing port 8080
- [ ] 22.8 Create Route resource with TLS edge termination
- [ ] 22.9 Create Secret resource for OPENAI_API_KEY
- [ ] 22.10 Add environment variable configuration loading secrets

## 23. Module 06 - Tests

- [ ] 23.1 Write unit tests for evaluation metrics (accuracy, relevance, faithfulness)
- [ ] 23.2 Write tests for semantic cache hit/miss scenarios
- [ ] 23.3 Write tests for token optimization with budget constraints
- [ ] 23.4 Write integration tests for distributed tracing span creation
- [ ] 23.5 Write tests for metrics collection and Prometheus export

## 24. Workshop Documentation - Module 03

- [ ] 24.1 Create site/content/modules/ROOT/pages/module-03-tools-mcp.adoc
- [ ] 24.2 Write introduction section (learning objectives, prerequisites, time estimate)
- [ ] 24.3 Write background section on MCP architecture and tool integration
- [ ] 24.4 Create Lab 3.1: Implement database tool
- [ ] 24.5 Create Lab 3.2: Implement external API tool
- [ ] 24.6 Create Lab 3.3: Configure MCP server
- [ ] 24.7 Create Lab 3.4: Build tool orchestration
- [ ] 24.8 Add knowledge check questions
- [ ] 24.9 Add challenge exercise (implement custom tool)

## 25. Workshop Documentation - Module 04

- [ ] 25.1 Create site/content/modules/ROOT/pages/module-04-chatbots-to-agents.adoc
- [ ] 25.2 Write introduction section (learning objectives, prerequisites, time estimate)
- [ ] 25.3 Write background section on ReAct pattern and agent architectures
- [ ] 25.4 Create Lab 4.1: Implement ReAct agent
- [ ] 25.5 Create Lab 4.2: Add stateful memory
- [ ] 25.6 Create Lab 4.3: Build multi-agent orchestration
- [ ] 25.7 Create Lab 4.4: Implement task decomposition
- [ ] 25.8 Add knowledge check questions
- [ ] 25.9 Add challenge exercise (create specialized agent)

## 26. Workshop Documentation - Module 05

- [ ] 26.1 Create site/content/modules/ROOT/pages/module-05-security-guardrails.adoc
- [ ] 26.2 Write introduction section (learning objectives, prerequisites, time estimate)
- [ ] 26.3 Write background section on AI security threats and defense strategies
- [ ] 26.4 Create Lab 5.1: Implement prompt injection defense
- [ ] 26.5 Create Lab 5.2: Build PII masking service
- [ ] 26.6 Create Lab 5.3: Add output validation
- [ ] 26.7 Create Lab 5.4: Implement access control
- [ ] 26.8 Create Lab 5.5: Build security audit logging
- [ ] 26.9 Add knowledge check questions with attack scenarios
- [ ] 26.10 Add challenge exercise (implement additional security layer)

## 27. Workshop Documentation - Module 06

- [ ] 27.1 Create site/content/modules/ROOT/pages/module-06-enterprise-production.adoc
- [ ] 27.2 Write introduction section (learning objectives, prerequisites, time estimate)
- [ ] 27.3 Write background section on production AI operations and observability
- [ ] 27.4 Create Lab 6.1: Build evaluation framework
- [ ] 27.5 Create Lab 6.2: Implement distributed tracing
- [ ] 27.6 Create Lab 6.3: Add metrics and monitoring
- [ ] 27.7 Create Lab 6.4: Implement caching strategies
- [ ] 27.8 Create Lab 6.5: Deploy to OpenShift
- [ ] 27.9 Add knowledge check questions
- [ ] 27.10 Add challenge exercise (optimize performance)

## 28. Integration & Polish

- [ ] 28.1 Update site navigation (nav.adoc) with new modules
- [ ] 28.2 Add cross-references between modules
- [ ] 28.3 Create sample query collections for testing each module
- [ ] 28.4 Write troubleshooting guide for common issues
- [ ] 28.5 Update main README with module overview and setup instructions
- [ ] 28.6 Add architecture diagrams for tools, agents, security, deployment
- [ ] 28.7 Review and update ADR.md with any additional decisions made during implementation
