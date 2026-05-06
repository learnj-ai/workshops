# Specialized Agents

## Introduction

While a single ReAct agent can handle diverse tasks, real-world systems often benefit from **specialization**. Just like human organizations have specialists (customer support, technical documentation, product experts), AI systems can have specialized agents that excel in specific domains.

In this chapter, you'll learn how to design specialized agents, each with focused expertise and tailored system prompts.

## The SpecializedAgent Interface

We define a common interface that all specialized agents implement:

```java
public interface SpecializedAgent {

    /**
     * Processes a request within this agent's domain of expertise.
     *
     * @param request The user's request
     * @return The agent's response
     */
    String process(String request);

    /**
     * Returns the name of this agent.
     *
     * @return Agent name
     */
    String getName();

    /**
     * Returns a description of this agent's capabilities.
     *
     * @return Agent description
     */
    String getDescription();
}
```

### Interface Design

**Simple Contract**: Each agent must implement three methods:
1. `process(request)`: Main logic for handling requests in the agent's domain
2. `getName()`: Human-readable name for logging and routing
3. `getDescription()`: Description used by the orchestrator to select the right agent

**Domain Focus**: Unlike the ReAct agent which is general-purpose, specialized agents are optimized for specific types of queries.

**Composability**: By standardizing on an interface, we can easily add new specialists without changing the orchestrator.

## CustomerSupportAgent

The customer support agent specializes in account-related queries and support tickets:

```java
@Component
public class CustomerSupportAgent implements SpecializedAgent {

    private final ChatModel chatModel;
    private final CustomerDataTool customerDataTool;

    public CustomerSupportAgent(
            ChatModel chatModel,
            CustomerDataTool customerDataTool) {
        this.chatModel = chatModel;
        this.customerDataTool = customerDataTool;
    }

    @Override
    public String process(String request) {
        String prompt = String.format("""
                You are a customer support specialist. Answer questions about:
                - Customer accounts and information
                - Support tickets and their status
                - Account issues and troubleshooting

                Available tools:
                - Look up customer information by ID
                - Search support tickets by status

                User question: %s

                Provide a helpful and professional response.
                """, request);

        return chatModel.chat(prompt);
    }

    @Override
    public String getName() {
        return "CustomerSupportAgent";
    }

    @Override
    public String getDescription() {
        return "Handles customer account queries, support tickets, and account-related issues";
    }
}
```

### Key Design Elements

**Focused System Prompt**: The prompt establishes the agent's role as a "customer support specialist" and clearly defines what it handles:
- Customer accounts
- Support tickets
- Account troubleshooting

**Tool Access**: The agent has access to `CustomerDataTool`, enabling it to look up real customer data and tickets.

**Professional Tone**: The prompt instructs the agent to be "helpful and professional"—setting the expected tone.

**Spring Component**: The `@Component` annotation makes this a Spring-managed bean that can be injected into the orchestrator.

### When to Use

Route queries to this agent when users ask about:
- "What's the status of my account?"
- "Show me all open tickets"
- "What's the email for customer 12345?"
- "How many pending support requests do we have?"

## TechnicalDocAgent

The technical documentation agent specializes in configuration, API documentation, and how-to guides:

```java
@Component
public class TechnicalDocAgent implements SpecializedAgent {

    private final ChatModel chatModel;

    public TechnicalDocAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String process(String request) {
        String prompt = String.format("""
                You are a technical documentation expert. Answer questions about:
                - API documentation and endpoints
                - Configuration and setup guides
                - Integration instructions
                - Technical specifications

                User question: %s

                Provide clear, technical guidance with examples where helpful.
                """, request);

        return chatModel.chat(prompt);
    }

    @Override
    public String getName() {
        return "TechnicalDocAgent";
    }

    @Override
    public String getDescription() {
        return "Provides technical documentation, API references, and configuration guidance";
    }
}
```

### Characteristics

**Technical Focus**: This agent is optimized for developers and technical users asking "how-to" questions.

**Example-Oriented**: The prompt encourages providing "examples where helpful"—a best practice for technical documentation.

**No Tool Access**: Notice this agent doesn't use tools—it relies on the LLM's knowledge (or could be augmented with a vector search over technical docs).

### When to Use

Route queries to this agent when users ask about:
- "How do I configure authentication?"
- "What are the available REST endpoints?"
- "Show me an example API request"
- "How do I integrate with the payment gateway?"

## ProductExpertAgent

The product expert agent specializes in product features, pricing, and business questions:

```java
@Component
public class ProductExpertAgent implements SpecializedAgent {

    private final ChatModel chatModel;

    public ProductExpertAgent(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @Override
    public String process(String request) {
        String prompt = String.format("""
                You are a product expert. Answer questions about:
                - Product features and capabilities
                - Subscription plans and pricing
                - Feature comparisons
                - Product roadmap and updates

                User question: %s

                Provide detailed product information and highlight value propositions.
                """, request);

        return chatModel.chat(prompt);
    }

    @Override
    public String getName() {
        return "ProductExpertAgent";
    }

    @Override
    public String getDescription() {
        return "Expert on product features, pricing plans, and capabilities";
    }
}
```

### Purpose

**Sales and Product Focus**: This agent is designed for pre-sales questions and product inquiries.

**Value Propositions**: The prompt instructs the agent to "highlight value propositions"—useful for helping potential customers understand benefits.

### When to Use

Route queries to this agent when users ask about:
- "What features are included in the Enterprise plan?"
- "How does this compare to competitors?"
- "What's coming in the next release?"
- "Can your product integrate with Salesforce?"

## Specialization Benefits

### 1. Improved Accuracy

By narrowing the agent's focus, you reduce the chance of confusion. A customer support agent won't accidentally talk about product features when asked about a ticket status.

### 2. Optimized Prompts

Each agent has a tailored system prompt that:
- Sets the right role and tone
- Defines the scope of knowledge
- Provides domain-specific instructions

### 3. Selective Tool Access

Agents only have access to the tools they need:
- `CustomerSupportAgent` has database tools
- `TechnicalDocAgent` might have documentation search tools
- `ProductExpertAgent` might have pricing API tools

This follows the **principle of least privilege**—agents can't accidentally access irrelevant data.

### 4. Easier Maintenance

When product features change, you update `ProductExpertAgent`. When API docs change, you update `TechnicalDocAgent`. Changes are isolated to the relevant specialist.

### 5. Parallel Development

Teams can work on different agents independently. The customer support team can refine their agent's prompt while the docs team works on theirs.

## Designing New Specialized Agents

When creating a new specialized agent, follow this pattern:

### Step 1: Define the Domain

What specific area does this agent cover? Examples:
- Billing and payments
- Security and compliance
- Analytics and reporting
- Developer tools

### Step 2: Write the System Prompt

Craft a prompt that:
- Establishes the agent's role
- Lists what it handles
- Sets the expected tone
- Mentions available tools (if any)

### Step 3: Identify Required Tools

What data or APIs does this agent need? Examples:
- Billing agent needs access to payment APIs
- Analytics agent needs access to reporting databases
- Security agent needs access to audit logs

### Step 4: Implement the Interface

```java
@Component
public class BillingAgent implements SpecializedAgent {

    private final ChatModel chatModel;
    private final BillingTool billingTool;

    public BillingAgent(ChatModel chatModel, BillingTool billingTool) {
        this.chatModel = chatModel;
        this.billingTool = billingTool;
    }

    @Override
    public String process(String request) {
        String prompt = String.format("""
                You are a billing specialist. Answer questions about:
                - Payment methods and invoices
                - Subscription billing and renewals
                - Refunds and credits

                User question: %s

                Provide clear billing information and next steps.
                """, request);

        return chatModel.chat(prompt);
    }

    @Override
    public String getName() {
        return "BillingAgent";
    }

    @Override
    public String getDescription() {
        return "Handles billing, payments, invoices, and subscription management";
    }
}
```

### Step 5: Register with Orchestrator

Add the new agent to the orchestrator (we'll cover this in the next chapter):

```java
this.agents.put("billing", billingAgent);
```

## Prompt Engineering for Specialists

### Best Practices

**1. Clear Role Definition**: Start with "You are a [role]" to set context.

**2. Scope Boundaries**: Explicitly list what the agent *does* handle. This helps the LLM stay focused.

**3. Tone Guidance**: Specify the expected tone:
- Customer support: "helpful and professional"
- Technical docs: "clear and technical"
- Product expert: "enthusiastic and value-focused"

**4. Tool Mentions**: If the agent has tools, mention them in the prompt so the LLM knows they're available.

**5. Output Format**: If you need structured output (JSON, markdown, etc.), specify the format.

### Example: Advanced Product Expert Prompt

```java
String prompt = String.format("""
        You are a product expert at TechCorp specializing in SaaS offerings.

        Your responsibilities:
        - Explain product features and benefits
        - Compare subscription plans (Free, Pro, Enterprise)
        - Discuss integrations and capabilities
        - Highlight competitive advantages

        Tone: Professional, enthusiastic, and value-focused.

        When discussing pricing:
        - Free: Up to 10 users, basic features
        - Pro: $29/user/month, advanced analytics, priority support
        - Enterprise: Custom pricing, SSO, dedicated account manager

        User question: %s

        Provide a detailed, value-oriented response. If comparing plans, use a table format.
        """, request);
```

This prompt provides:
- Clear role and responsibilities
- Specific pricing information
- Tone guidance
- Output format suggestion (table for comparisons)

## Testing Specialized Agents

Test each agent to ensure it stays within its domain:

```java
@Test
void customerSupportAgent_HandlesAccountQueries() {
    String response = customerSupportAgent.process("What's the status of customer 12345?");
    assertThat(response).isNotEmpty();
}

@Test
void technicalDocAgent_ProvidesConfiguration() {
    String response = technicalDocAgent.process("How do I configure OAuth?");
    assertThat(response).containsAnyOf("OAuth", "configuration", "authentication");
}

@Test
void productExpertAgent_DiscussesPricing() {
    String response = productExpertAgent.process("What's included in the Pro plan?");
    assertThat(response).containsAnyOf("Pro", "plan", "features");
}
```

## When NOT to Use Specialized Agents

Specialization has trade-offs. Don't create specialized agents if:

1. **Overlap is High**: If two agents would handle 80% of the same queries, combine them
2. **Volume is Low**: If an agent would only get 1-2 queries per week, it's not worth maintaining
3. **Complexity is Low**: If the domain is simple, a well-prompted general agent may suffice
4. **Maintenance Burden**: Each agent requires prompt tuning, testing, and updates

## Summary

Specialized agents provide:
- **Focused expertise** in specific domains
- **Optimized prompts** tailored to each area
- **Selective tool access** following least privilege
- **Easier maintenance** with isolated updates
- **Better accuracy** by reducing scope confusion

Key design principles:
- Implement the `SpecializedAgent` interface
- Write domain-focused system prompts
- Provide only necessary tools
- Set appropriate tone and output format
- Test to ensure agents stay within their scope

In the next chapter, we'll build the **MultiAgentOrchestrator** that intelligently routes requests to the right specialist and synthesizes collaborative responses.

---

**Next Chapter**: [04 - Multi-Agent Orchestration](./04-multi-agent-orchestrator.md)
