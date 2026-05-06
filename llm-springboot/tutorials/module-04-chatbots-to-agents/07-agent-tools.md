# Agent Tools

## Introduction

Tools are what transform agents from pure language models into systems that can interact with the real world. Through tools, agents can query databases, call APIs, read files, and perform actions. In this chapter, you'll learn how to create tools using LangChain4j's `@Tool` annotation and integrate them with agents.

## What are Agent Tools?

Agent tools are Java methods that:
1. Are annotated with `@Tool` to make them discoverable by the LLM
2. Have clear descriptions that help the LLM decide when to use them
3. Accept typed parameters with descriptions
4. Return string results that the agent can interpret

Think of tools as the agent's hands—they allow it to manipulate the world beyond text generation.

## The @Tool Annotation

LangChain4j provides the `@Tool` annotation to mark methods as agent-callable:

```java
@Tool("Retrieves customer information by customer ID including name, email, and subscription plan")
public String getCustomerInfo(@P("The customer ID to retrieve information for") String customerId) {
    // Implementation
}
```

### Key Elements

**@Tool Annotation**: Describes what the tool does. The LLM uses this description to decide when to call the tool.

**@P Annotation**: Describes each parameter. Helps the LLM provide correct arguments.

**String Return**: Tools return strings so the LLM can interpret results as observations.

**Clear Names**: Use descriptive method names like `getCustomerInfo`, not `query1`.

## CustomerDataTool Implementation

Our first tool provides access to customer data:

```java
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

### Design Principles

**Database Access**: Uses Spring's `JdbcTemplate` for database queries.

**Error Messages**: Returns user-friendly error messages (not stack traces) that the agent can interpret.

**Validation**: Validates input (e.g., status must be "open", "pending", or "closed").

**Structured Output**: Returns well-formatted text that's easy for the LLM to parse.

**Logging**: Logs every tool invocation for debugging and auditing.

**Limits**: LIMIT 10 prevents returning huge result sets.

## WeatherTool Example

Tools can also call external APIs:

```java
@Component
public class WeatherTool {
    private static final Logger log = LoggerFactory.getLogger(WeatherTool.class);
    private final RestTemplate restTemplate;

    @Value("${weather.api.key}")
    private String apiKey;

    public WeatherTool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Tool("Gets the current weather for a specified city")
    public String getCurrentWeather(@P("The city name to get weather for") String city) {
        log.debug("Tool invoked: getCurrentWeather({})", city);

        try {
            String url = String.format(
                "https://api.weatherapi.com/v1/current.json?key=%s&q=%s",
                apiKey,
                URLEncoder.encode(city, StandardCharsets.UTF_8)
            );

            WeatherResponse response = restTemplate.getForObject(url, WeatherResponse.class);

            if (response == null || response.current == null) {
                return "Unable to retrieve weather data for " + city;
            }

            return String.format("""
                Current weather in %s:
                - Condition: %s
                - Temperature: %.1f°F
                - Feels like: %.1f°F
                - Humidity: %d%%
                - Wind: %.1f mph
                """,
                city,
                response.current.condition.text,
                response.current.temp_f,
                response.current.feelslike_f,
                response.current.humidity,
                response.current.wind_mph
            );

        } catch (Exception e) {
            log.error("Error fetching weather for city: {}", city, e);
            return "Error retrieving weather data for " + city;
        }
    }

    record WeatherResponse(Current current) {}
    record Current(Condition condition, double temp_f, double feelslike_f, int humidity, double wind_mph) {}
    record Condition(String text) {}
}
```

### API Integration Patterns

**RestTemplate**: Spring's HTTP client for calling external APIs.

**URL Encoding**: Properly encode city names with spaces or special characters.

**Typed Responses**: Use records to deserialize JSON responses.

**Error Handling**: Catch exceptions and return error messages (don't crash).

**Configuration**: API keys are externalized to properties files.

## Tool Design Best Practices

### 1. Clear, Descriptive Names

**Good**: `getCustomerInfo`, `searchTickets`, `getCurrentWeather`
**Bad**: `query`, `fetch`, `getData`

The LLM relies on method names to understand what the tool does.

### 2. Detailed Tool Descriptions

```java
// Good: Specific and actionable
@Tool("Retrieves customer information by customer ID including name, email, and subscription plan")

// Bad: Too vague
@Tool("Gets data")
```

### 3. Parameter Descriptions

```java
// Good: Explains what to provide
@P("The customer ID to retrieve information for")

// Bad: Redundant with parameter name
@P("customerId")
```

### 4. Validation

Always validate inputs:
```java
if (!List.of("open", "pending", "closed").contains(status)) {
    return "Invalid status. Please use one of: open, pending, closed";
}
```

This helps the agent correct its mistakes.

### 5. Structured Output

Return consistently formatted text:
```java
return String.format("""
    Customer Information:
    - ID: %s
    - Name: %s
    - Email: %s
    """, id, name, email);
```

Consistent structure makes it easier for LLMs to extract information.

### 6. Error Messages

Return actionable error messages:
```java
// Good
return "Customer not found: No customer exists with ID " + customerId;

// Bad
return "Error";
```

The agent can use detailed errors to adjust its approach.

## Integrating Tools with Agents

### Manual Integration (ReActAgent)

Our ReAct agent manually maps tool names to methods:

```java
return switch (toolName) {
    case "getCustomerInfo" -> customerDataTool.getCustomerInfo(parameters);
    case "searchTickets" -> customerDataTool.searchTickets(parameters);
    case "getCurrentWeather" -> weatherTool.getCurrentWeather(parameters);
    default -> "Error: Unknown tool: " + toolName;
};
```

This gives full control but requires manual registration.

### Automatic Integration (LangChain4j Agents)

LangChain4j can automatically discover and execute `@Tool` methods:

```java
@Service
public class AutomaticToolAgent {
    private final ChatModel chatModel;
    private final CustomerDataTool customerDataTool;
    private final WeatherTool weatherTool;

    public String chat(String message) {
        return AiServices.builder(Assistant.class)
                .chatLanguageModel(chatModel)
                .tools(customerDataTool, weatherTool)
                .build()
                .chat(message);
    }

    interface Assistant {
        String chat(String message);
    }
}
```

LangChain4j automatically:
- Discovers all `@Tool` methods
- Generates function call descriptions
- Handles parameter extraction
- Executes the correct tool
- Returns observations to the LLM

## Creating Custom Tools

Let's create a new tool for managing billing:

```java
@Component
public class BillingTool {
    private static final Logger log = LoggerFactory.getLogger(BillingTool.class);
    private final JdbcTemplate jdbcTemplate;

    public BillingTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool("Retrieves billing information for a customer including current balance and last payment")
    public String getBillingInfo(@P("The customer ID to get billing info for") String customerId) {
        log.debug("Tool invoked: getBillingInfo({})", customerId);

        try {
            String sql = """
                SELECT customer_id, current_balance, last_payment_date, last_payment_amount
                FROM billing
                WHERE customer_id = ?
                """;

            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, customerId);

            if (results.isEmpty()) {
                return "No billing information found for customer ID: " + customerId;
            }

            Map<String, Object> billing = results.get(0);
            return String.format("""
                Billing Information for Customer %s:
                - Current Balance: $%.2f
                - Last Payment: $%.2f on %s
                """,
                billing.get("customer_id"),
                billing.get("current_balance"),
                billing.get("last_payment_amount"),
                billing.get("last_payment_date")
            );

        } catch (Exception e) {
            log.error("Error retrieving billing info for customer: {}", customerId, e);
            return "Error retrieving billing information. Please try again later.";
        }
    }

    @Tool("Creates a refund request for a customer")
    public String createRefund(
            @P("The customer ID to refund") String customerId,
            @P("The refund amount in dollars") double amount,
            @P("The reason for the refund") String reason) {

        log.info("Creating refund: customer={}, amount={}, reason={}", customerId, amount, reason);

        try {
            String sql = """
                INSERT INTO refund_requests (customer_id, amount, reason, status, created_at)
                VALUES (?, ?, ?, 'pending', NOW())
                """;

            jdbcTemplate.update(sql, customerId, amount, reason);

            return String.format("Refund request created: $%.2f for customer %s. Status: pending review.", amount, customerId);

        } catch (Exception e) {
            log.error("Error creating refund for customer: {}", customerId, e);
            return "Error creating refund request. Please try again later.";
        }
    }
}
```

### Multi-Parameter Tools

The `createRefund` tool shows how to handle multiple parameters:
- Each parameter has its own `@P` description
- The LLM extracts all parameters from the conversation context
- The tool validates and executes the operation

## Tool Security Considerations

### 1. Authorization

Check if the user has permission to access the data:
```java
@Tool("Retrieves customer information")
public String getCustomerInfo(String customerId, String requestingUserId) {
    if (!authService.canAccessCustomer(requestingUserId, customerId)) {
        return "Access denied: You don't have permission to view this customer.";
    }
    // Proceed with query
}
```

### 2. Input Validation

Always validate and sanitize inputs:
```java
if (customerId == null || !customerId.matches("\\d+")) {
    return "Invalid customer ID format. Must be a number.";
}
```

### 3. Rate Limiting

Prevent abuse of external API tools:
```java
if (!rateLimiter.tryAcquire()) {
    return "Rate limit exceeded. Please try again in a few minutes.";
}
```

### 4. Audit Logging

Log all tool executions for compliance:
```java
auditLog.record(AuditEvent.builder()
    .tool("getCustomerInfo")
    .user(requestingUserId)
    .customerId(customerId)
    .timestamp(Instant.now())
    .build());
```

## Testing Tools

```java
@Test
void getCustomerInfo_ReturnsValidData() {
    String result = customerDataTool.getCustomerInfo("12345");

    assertThat(result).contains("Customer Information");
    assertThat(result).contains("12345");
}

@Test
void searchTickets_ValidatesStatus() {
    String result = customerDataTool.searchTickets("invalid-status");

    assertThat(result).contains("Invalid status");
    assertThat(result).contains("open, pending, closed");
}

@Test
void getCurrentWeather_HandlesInvalidCity() {
    String result = weatherTool.getCurrentWeather("InvalidCity123");

    assertThat(result).containsAnyOf("Unable to retrieve", "Error");
}
```

## Summary

Agent tools enable LLMs to interact with the real world by:
- Querying databases
- Calling external APIs
- Performing actions (create, update, delete)
- Accessing file systems or other resources

Key concepts:
- `@Tool`: Marks methods as agent-callable
- `@P`: Describes parameters for the LLM
- Clear descriptions help the LLM select the right tool
- Structured output makes results easy to interpret
- Error handling returns actionable messages
- Validation prevents misuse

Best practices:
- Use descriptive names and detailed descriptions
- Validate all inputs
- Return structured, parseable output
- Handle errors gracefully
- Log all tool invocations
- Implement security controls (auth, rate limiting, audit logs)

In the next chapter, we'll build the **AgentController** that exposes our agent system via REST API.

---

**Next Chapter**: [08 - Agent Controller](./08-agent-controller.md)
