# Chapter: The @Tool Annotation - Making Functions Callable by AI

## Introduction: Teaching AI to Use Your Functions

Imagine you've built a Java method that retrieves customer information from a database. It works perfectly when you call it from your code. But how do you make it available to an AI language model? How does the LLM know this function exists, what it does, and what parameters it needs?

This is where the **@Tool** annotation comes in. It's the bridge between your Java code and the AI's reasoning engine. Think of it as writing a contract that says: "Hey LLM, you can call this method whenever you need to perform this specific task."

The `@Tool` annotation transforms ordinary Java methods into **AI-callable functions** that can be invoked autonomously by language models during conversations.

## How It Works: From Annotation to Function Call

When you mark a method with `@Tool`, LangChain4j does several things behind the scenes:

### 1. Tool Registration
At application startup, LangChain4j scans for methods marked with `@Tool` and builds a registry of available tools. Each tool includes:
- The method name
- A description of what it does (from the annotation)
- Parameter names and descriptions (from `@P` annotations)
- The parameter types

### 2. Schema Generation
The framework converts your method signature into a JSON schema that LLMs understand. This schema is sent to the LLM along with every request, allowing the model to "see" what tools are available.

### 3. Invocation During Conversation
When processing a user query:
1. The LLM analyzes the query and determines if a tool is needed
2. If yes, the LLM generates a structured tool call (function name + parameters)
3. LangChain4j intercepts this, invokes your Java method with the parameters
4. The result is fed back to the LLM
5. The LLM incorporates the result into its response

## Anatomy of a Tool Definition

Let's break down a real example from the CustomerDataTool:

```java
@Tool("Retrieves customer information by customer ID including name, email, and subscription plan")
public String getCustomerInfo(@P("The customer ID to retrieve information for") String customerId) {
    // Method implementation
}
```

### The @Tool Annotation
```java
@Tool("Retrieves customer information...")
```

This description is **critical**. The LLM reads this to understand:
- **WHEN** to call this tool (when the user asks about customer information)
- **WHAT** the tool does (retrieves specific customer data)
- **WHAT DATA** it returns (name, email, subscription plan)

**Best Practices for Tool Descriptions:**
- Be specific about what the tool does
- Mention key data fields it returns
- Use action verbs (retrieves, searches, calculates, fetches)
- Keep it concise but informative (1-2 sentences)

### The @P Annotation (Parameter Description)
```java
@P("The customer ID to retrieve information for") String customerId
```

Each parameter should have a `@P` annotation that tells the LLM:
- **WHAT** the parameter represents
- **FORMAT** expected (if relevant, e.g., "ISO date format", "JSON string")
- **VALID VALUES** (e.g., "one of: open, pending, closed")

The LLM uses these descriptions to extract the right values from user queries. For example:
- User asks: "What's the email for customer 12345?"
- LLM extracts: `customerId = "12345"`
- LLM calls: `getCustomerInfo("12345")`

### Return Type and Format
```java
public String getCustomerInfo(...)
```

Tools typically return `String` because:
- It's the most flexible format for LLMs to process
- You can return plain text, formatted text, JSON, or even markdown
- The LLM can parse and understand various string formats

However, you can also return:
- Primitive types: `int`, `boolean`, `double`
- Complex objects: The framework will serialize them
- Collections: `List<String>`, `Map<String, Object>`

**Best Practice**: Return human-readable strings that the LLM can directly incorporate into responses.

## Real-World Examples from Module 03

### Example 1: Database Query Tool

```java
@Tool("Retrieves customer information by customer ID including name, email, and subscription plan")
public String getCustomerInfo(@P("The customer ID to retrieve information for") String customerId) {
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
}
```

**Why This Works Well:**
- Clear description tells the LLM this is for customer lookups
- Single parameter with clear description
- Returns formatted, human-readable text
- Handles error case (customer not found)

### Example 2: API Integration Tool

```java
@Tool("Retrieves current weather information for a specified city including temperature and conditions")
public String getCurrentWeather(@P("The city name to get weather for") String city) {
    // For production: call real weather API
    // String url = String.format("https://api.openweathermap.org/data/2.5/weather?q=%s&appid=%s", city, apiKey);

    return String.format("""
        Current Weather in %s:
        - Temperature: 19°C (66°F)
        - Conditions: Partly cloudy
        - Humidity: 62%%
        - Wind: 10 km/h W
        """, city);
}
```

**Why This Works Well:**
- Description clearly states it's for weather data
- Specifies what data is returned (temperature, conditions)
- Simple string parameter (city name)
- Returns structured, readable information

### Example 3: Search Tool with Validation

```java
@Tool("Searches support tickets by status. Valid statuses are: open, pending, closed")
public String searchTickets(@P("The ticket status to search for (open, pending, or closed)") String status) {
    // Validate the parameter
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

    // Format results...
    return response.toString();
}
```

**Why This Works Well:**
- Description includes valid values for the parameter
- Parameter description reinforces valid options
- Validation provides helpful error messages
- Returns no results gracefully

## Common Patterns and Best Practices

### Pattern 1: Single Responsibility
Each tool should do **one thing** well. Don't create a mega-tool that searches customers AND tickets AND products.

**Good:**
```java
@Tool("Retrieves customer information by customer ID")
public String getCustomerInfo(String customerId) { ... }

@Tool("Searches support tickets by status")
public String searchTickets(String status) { ... }
```

**Bad:**
```java
@Tool("Retrieves various types of data based on type and parameters")
public String getData(String dataType, String param1, String param2) { ... }
```

### Pattern 2: Descriptive Error Messages
Tools should return helpful messages when things go wrong, not throw exceptions:

```java
@Tool("Retrieves customer information by customer ID")
public String getCustomerInfo(String customerId) {
    try {
        // Query logic
        if (results.isEmpty()) {
            return "Customer not found: No customer exists with ID " + customerId;
        }
        return formatCustomerInfo(results.get(0));
    } catch (Exception e) {
        log.error("Error retrieving customer info", e);
        return "Error retrieving customer information. Please try again later.";
    }
}
```

The LLM can then convey this to the user naturally: "I'm sorry, but I couldn't find a customer with that ID."

### Pattern 3: Parameter Types
Use appropriate parameter types:

```java
// Good: String for identifiers, even if they're numeric
@Tool("Gets customer by ID")
public String getCustomer(@P("Customer ID") String customerId) { ... }

// Good: Enum or String with validation for limited choices
@Tool("Searches tickets by status")
public String searchTickets(@P("Status: open, pending, or closed") String status) { ... }

// Good: Primitive types when calculation is involved
@Tool("Calculates monthly payment")
public String calculatePayment(@P("Principal amount") double principal,
                               @P("Interest rate (annual %)") double rate,
                               @P("Loan term (months)") int months) { ... }
```

### Pattern 4: Rich Return Values
Return information in a format the LLM can easily parse:

```java
// Good: Structured but readable
return """
    Customer Information:
    - ID: 12345
    - Name: Alice Johnson
    - Email: alice@example.com
    - Plan: Premium
    """;

// Also good: JSON if you need programmatic parsing downstream
return """
    {
      "id": "12345",
      "name": "Alice Johnson",
      "email": "alice@example.com",
      "plan": "premium"
    }
    """;

// Avoid: Unstructured blobs
return "12345 Alice Johnson alice@example.com premium";
```

## How the LLM Decides to Call Tools

The language model uses several signals to determine if and when to call tools:

### User Intent Matching
The LLM matches user queries to tool descriptions:

- User: "What's customer 12345's email?"
- Match: Tool description mentions "customer information" and "email"
- Action: Call `getCustomerInfo("12345")`

### Parameter Extraction
The LLM extracts parameters from natural language:

- User: "Show me all open support tickets"
- Extracted: status = "open"
- Action: Call `searchTickets("open")`

### Multi-Tool Orchestration
The LLM can call multiple tools in sequence:

- User: "What's the weather where customer 12345 lives?"
- Step 1: Call `getCustomerInfo("12345")` to get location
- Step 2: Parse location from result
- Step 3: Call `getCurrentWeather(location)`
- Step 4: Synthesize final response

## Key Takeaways

- **@Tool makes Java methods callable by AI models** by providing metadata about what the method does
- **@P annotations describe parameters** so the LLM knows what values to extract from user queries
- **Tool descriptions are critical** - they guide the LLM's decision on when to use each tool
- **Return strings are most flexible** - the LLM can parse and incorporate them naturally
- **Error handling should be graceful** - return error messages, don't throw exceptions
- **Keep tools focused** - one tool, one responsibility
- **Validate parameters** - don't assume the LLM will always pass valid values

## Next Steps: Building Your First Database Tool

Now that you understand how the `@Tool` annotation works and how to write effective tool descriptions, you're ready to build a real database tool.

In the next chapter, **CustomerDataTool**, you'll learn how to:
- Connect to PostgreSQL using Spring JdbcTemplate
- Write parameterized SQL queries safely
- Format database results for LLM consumption
- Handle edge cases like missing data

---

**Continue to the next chapter to build a production-ready database tool!**
