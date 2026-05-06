# Chapter: CustomerDataTool - Database Integration for AI

## Introduction: Giving AI Access to Your Data

One of the most powerful capabilities of modern AI assistants is their ability to query real-time data from databases. Instead of hallucinating customer information or relying on stale training data, your AI can fetch accurate, up-to-date information directly from your production systems.

**CustomerDataTool** demonstrates how to safely expose database operations to language models using Spring Boot's JdbcTemplate. It provides two essential capabilities: retrieving customer information by ID and searching support tickets by status.

Think of this tool as your AI's database interface—it translates natural language queries into SQL, executes them safely, and returns formatted results that the LLM can understand and present to users.

## Code Deep Dive

Let's examine the complete CustomerDataTool implementation:

```java
package com.techcorp.assistant.module03.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CustomerDataTool {
    private static final Logger log = LoggerFactory.getLogger(CustomerDataTool.class);
    private final JdbcTemplate jdbcTemplate;

    public CustomerDataTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool("Retrieves customer information by customer ID including name, email, and subscription plan")
    public String getCustomerInfo(@P("The customer ID to retrieve information for") String customerId) {
        log.debug("Tool invoked: getCustomerInfo({})", customerId);

        try {
            String sql = """
                SELECT customer_id, name, email, subscription_plan, created_at
                FROM customers
                WHERE customer_id = ?
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, customerId);

            if (results.isEmpty()) {
                return "Customer not found: No customer exists with ID " + customerId;
            }

            Map<String, Object> customer = results.get(0);
            return String.format("""
                Customer Information:
                - ID: %s
                - Name: %s
                - Email: %s
                - Subscription Plan: %s
                - Member Since: %s
                """,
                customer.get("customer_id"),
                customer.get("name"),
                customer.get("email"),
                customer.get("subscription_plan"),
                customer.get("created_at")
            );
        } catch (Exception e) {
            log.error("Error retrieving customer info for ID: {}", customerId, e);
            return "Error retrieving customer information. Please try again later.";
        }
    }

    @Tool("Searches support tickets by status. Valid statuses are: open, pending, closed")
    public String searchTickets(@P("The ticket status to search for (open, pending, or closed)") String status) {
        log.debug("Tool invoked: searchTickets({})", status);

        try {
            // Validate status parameter
            String normalizedStatus = status.toLowerCase().trim();
            if (!List.of("open", "pending", "closed").contains(normalizedStatus)) {
                return "Invalid status. Please use one of: open, pending, closed";
            }

            String sql = """
                SELECT t.ticket_id, t.customer_id, c.name as customer_name,
                       t.subject, t.status, t.created_at
                FROM support_tickets t
                JOIN customers c ON t.customer_id = c.customer_id
                WHERE t.status = ?
                ORDER BY t.created_at DESC
                LIMIT 10
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, normalizedStatus);

            if (results.isEmpty()) {
                return "No tickets found with status: " + normalizedStatus;
            }

            StringBuilder response = new StringBuilder();
            response.append(String.format("Found %d %s ticket(s):\n\n", results.size(), normalizedStatus));

            for (Map<String, Object> ticket : results) {
                response.append(String.format("""
                    Ticket #%s
                    - Customer: %s (ID: %s)
                    - Subject: %s
                    - Status: %s
                    - Created: %s

                    """,
                    ticket.get("ticket_id"),
                    ticket.get("customer_name"),
                    ticket.get("customer_id"),
                    ticket.get("subject"),
                    ticket.get("status"),
                    ticket.get("created_at")
                ));
            }

            return response.toString();
        } catch (Exception e) {
            log.error("Error searching tickets with status: {}", status, e);
            return "Error searching tickets. Please try again later.";
        }
    }
}
```

## Architecture and Design Decisions

### Spring Component
```java
@Component
public class CustomerDataTool {
```

The `@Component` annotation makes this class a Spring-managed bean, which means:
- Spring will automatically create an instance at startup
- It can be dependency-injected into other components (like ToolOrchestrator)
- Its lifecycle is managed by the Spring container

### Dependency Injection
```java
private final JdbcTemplate jdbcTemplate;

public CustomerDataTool(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
}
```

**Why JdbcTemplate?**
- Spring Boot automatically creates a JdbcTemplate bean configured with the database connection
- It handles connection pooling, resource management, and exception translation
- It's simpler than JPA/Hibernate for straightforward queries
- Perfect for read-only operations like these tools

**Why constructor injection?**
- Makes the dependency explicit and required
- Enables immutability (final field)
- Easier to test (can pass mock JdbcTemplate)
- Recommended best practice over field injection

### Logging
```java
private static final Logger log = LoggerFactory.getLogger(CustomerDataTool.class);

log.debug("Tool invoked: getCustomerInfo({})", customerId);
```

**Why log tool invocations?**
- Debugging: See what the LLM is actually calling
- Auditing: Track which tools are used most frequently
- Monitoring: Detect unusual patterns or potential abuse
- Troubleshooting: Understand the flow when issues occur

**Use DEBUG level** because:
- Production systems don't need to log every tool call by default
- You can enable DEBUG logging when investigating issues
- Keeps INFO logs focused on important application events

## Tool Implementation Details

### Tool 1: Get Customer Information

#### The Query
```java
String sql = """
    SELECT customer_id, name, email, subscription_plan, created_at
    FROM customers
    WHERE customer_id = ?
    """;
```

**Text Blocks (Java 15+)**: The `"""` syntax creates multiline strings without escape characters, making SQL much more readable.

**Parameterized Query**: The `?` is a placeholder that prevents SQL injection. Never concatenate user input directly into SQL!

**Bad (Vulnerable to SQL Injection):**
```java
String sql = "SELECT * FROM customers WHERE customer_id = '" + customerId + "'";
// If customerId = "'; DROP TABLE customers; --"
// You've just deleted your customers table!
```

**Good (Safe):**
```java
jdbcTemplate.queryForList(sql, customerId);
// JdbcTemplate safely escapes the parameter
```

#### Executing the Query
```java
List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, customerId);
```

**queryForList** returns a list of maps where:
- Each Map represents one row
- Keys are column names
- Values are the column values (as Objects)

Example result:
```java
[
  {
    "customer_id": "12345",
    "name": "Alice Johnson",
    "email": "alice@example.com",
    "subscription_plan": "premium",
    "created_at": "2024-01-15 10:30:00"
  }
]
```

#### Handling No Results
```java
if (results.isEmpty()) {
    return "Customer not found: No customer exists with ID " + customerId;
}
```

**Why return a message instead of throwing an exception?**
- The LLM can present this naturally to the user
- It's not an error—the customer simply doesn't exist
- Exceptions should be reserved for actual failures (database down, query syntax error)

#### Formatting the Response
```java
return String.format("""
    Customer Information:
    - ID: %s
    - Name: %s
    - Email: %s
    - Subscription Plan: %s
    - Member Since: %s
    """,
    customer.get("customer_id"),
    customer.get("name"),
    customer.get("email"),
    customer.get("subscription_plan"),
    customer.get("created_at")
);
```

**Why this format?**
- Human-readable: The LLM can quote it directly or rephrase naturally
- Structured: Clear labeling of each field
- Complete: Includes all requested information
- Parseable: The LLM can extract specific fields if needed

The LLM can then respond with something like:
> "I found the customer! Alice Johnson (alice@example.com) is on our premium plan and has been a member since January 15, 2024."

### Tool 2: Search Support Tickets

#### Parameter Validation
```java
String normalizedStatus = status.toLowerCase().trim();
if (!List.of("open", "pending", "closed").contains(normalizedStatus)) {
    return "Invalid status. Please use one of: open, pending, closed";
}
```

**Why validate?**
- The LLM might pass unexpected values
- Prevents SQL errors if the status column has a CHECK constraint
- Provides clear feedback when something goes wrong

**Why normalize (toLowerCase, trim)?**
- User might ask "Show me Open tickets" (capitalized)
- LLM might pass "open " with trailing space
- Normalization makes the tool more robust

#### The Join Query
```java
String sql = """
    SELECT t.ticket_id, t.customer_id, c.name as customer_name,
           t.subject, t.status, t.created_at
    FROM support_tickets t
    JOIN customers c ON t.customer_id = c.customer_id
    WHERE t.status = ?
    ORDER BY t.created_at DESC
    LIMIT 10
    """;
```

**Key decisions:**
- **JOIN with customers**: Includes customer name in results so the LLM doesn't need to make separate calls
- **ORDER BY created_at DESC**: Most recent tickets first (usually most relevant)
- **LIMIT 10**: Prevents overwhelming the LLM with hundreds of results
  - Too many results increase token usage and processing time
  - If there are more, the user can ask for more specific criteria

#### Building Dynamic Responses
```java
StringBuilder response = new StringBuilder();
response.append(String.format("Found %d %s ticket(s):\n\n", results.size(), normalizedStatus));

for (Map<String, Object> ticket : results) {
    response.append(String.format("""
        Ticket #%s
        - Customer: %s (ID: %s)
        - Subject: %s
        - Status: %s
        - Created: %s

        """,
        ticket.get("ticket_id"),
        ticket.get("customer_name"),
        ticket.get("customer_id"),
        ticket.get("subject"),
        ticket.get("status"),
        ticket.get("created_at")
    ));
}
```

**Why StringBuilder?**
- Efficient for concatenating many strings in a loop
- Avoids creating intermediate String objects

**Why include a summary?**
- "Found 3 open ticket(s)" gives the LLM context before details
- Helps the LLM structure its response better

## Error Handling Strategy

Both tools use a consistent error handling pattern:

```java
try {
    // Tool logic
} catch (Exception e) {
    log.error("Error retrieving customer info for ID: {}", customerId, e);
    return "Error retrieving customer information. Please try again later.";
}
```

**Why catch Exception broadly?**
- Database exceptions (connection failures, query errors)
- Data exceptions (unexpected null values)
- Any other runtime issues
- All are handled uniformly—return a friendly message

**Why log the exception?**
- Developers can see the stack trace and diagnose issues
- Operations teams can monitor error rates
- But don't expose error details to the LLM (security risk)

**What the user experiences:**
Instead of seeing a stack trace or technical error, they get:
> "I'm sorry, I encountered an error retrieving that information. Please try again in a moment."

## Database Schema

The tool expects these database tables:

```sql
CREATE TABLE customers (
    customer_id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    subscription_plan VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE support_tickets (
    ticket_id SERIAL PRIMARY KEY,
    customer_id VARCHAR(50) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('open', 'pending', 'closed')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (customer_id) REFERENCES customers(customer_id)
);

CREATE INDEX idx_tickets_status ON support_tickets(status);
CREATE INDEX idx_tickets_customer ON support_tickets(customer_id);
```

**Design notes:**
- **VARCHAR customer_id**: Allows flexible ID formats (not just integers)
- **CHECK constraint on status**: Ensures only valid values in the database
- **Indexes**: Optimizes the WHERE clauses we use in our queries
- **Foreign key**: Ensures referential integrity between tickets and customers

## Example Usage Scenarios

### Scenario 1: Simple Customer Lookup

**User Query:** "What's the email for customer 12345?"

**LLM Reasoning:**
1. Identifies that customer information is needed
2. Extracts customer ID: "12345"
3. Calls `getCustomerInfo("12345")`

**Tool Execution:**
```sql
SELECT customer_id, name, email, subscription_plan, created_at
FROM customers
WHERE customer_id = '12345'
```

**Tool Returns:**
```
Customer Information:
- ID: 12345
- Name: Alice Johnson
- Email: alice.johnson@example.com
- Subscription Plan: premium
- Member Since: 2024-01-15 10:30:00
```

**LLM Response to User:**
> "The email for customer 12345 (Alice Johnson) is alice.johnson@example.com."

### Scenario 2: Ticket Search

**User Query:** "Show me all open support tickets"

**LLM Reasoning:**
1. Identifies need to search tickets
2. Extracts status: "open"
3. Calls `searchTickets("open")`

**Tool Returns:**
```
Found 4 open ticket(s):

Ticket #1
- Customer: Alice Johnson (ID: 12345)
- Subject: Cannot access dashboard after login
- Status: open
- Created: 2024-05-01 09:15:00

Ticket #2
- Customer: Bob Smith (ID: 12346)
- Subject: API rate limit exceeded error
- Status: open
- Created: 2024-04-30 14:22:00

...
```

**LLM Response to User:**
> "I found 4 open support tickets. The most recent is from Alice Johnson (ID 12345) about not being able to access the dashboard after login, created on May 1st. Would you like more details on any specific ticket?"

### Scenario 3: Multi-Step Query

**User Query:** "What open tickets does customer 12345 have?"

This is interesting because it requires combining information:

**LLM Reasoning:**
1. Calls `searchTickets("open")` to get all open tickets
2. Filters results for customer_id = 12345 (or asks for clarification)

*Alternatively*, a more sophisticated implementation might add a tool:
```java
@Tool("Retrieves support tickets for a specific customer")
public String getCustomerTickets(
    @P("Customer ID") String customerId,
    @P("Optional status filter") String status
) {
    // Query with both customer_id AND status filters
}
```

## Key Takeaways

- **CustomerDataTool demonstrates database integration** using Spring JdbcTemplate
- **@Component makes it a Spring bean** that can be injected into other services
- **JdbcTemplate provides safe, parameterized queries** that prevent SQL injection
- **Tool methods return human-readable strings** that LLMs can directly incorporate
- **Error handling returns friendly messages** rather than throwing exceptions
- **Validation prevents invalid parameters** from causing query failures
- **Logging tracks tool invocations** for debugging and monitoring
- **JOINs and LIMIT clauses optimize queries** for AI consumption
- **Text blocks make SQL readable** and maintainable

## Next Steps: Integrating External APIs

Now that you've seen how to build a database tool, you're ready to learn how to integrate external REST APIs.

In the next chapter, **WeatherTool**, you'll discover how to:
- Call external REST APIs from your tools
- Handle API failures gracefully
- Work with external service configuration
- Mock external services for testing and development

---

**Continue to the next chapter to add external API capabilities to your AI assistant!**
