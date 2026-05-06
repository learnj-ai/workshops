# Task Decomposition

## Introduction

Complex tasks often require multiple steps that build on each other. A human project manager breaks down "Launch new feature" into subtasks like "Design mockups", "Implement backend", "Write tests", each with dependencies.

The `TaskDecomposer` brings this capability to AI agents—it uses an LLM to break complex tasks into subtasks, tracks dependencies, and executes them in the correct order.

## The Task Decomposition Pattern

### What is Task Decomposition?

**Input**: A complex task like "Research our top customers and summarize their common issues"

**Output**: A structured plan with subtasks and dependencies:
```
task1: Identify top customers by subscription plan
task2 (depends on task1): For each top customer, search their open tickets
task3 (depends on task2): Summarize common issues across all tickets
```

**Execution**: The decomposer executes subtasks in topological order, passing results from dependencies to dependent tasks.

### Why It Matters

**Without Decomposition**:
- Complex tasks fail or produce incomplete answers
- The LLM tries to do everything in one shot
- No structure or progress tracking

**With Decomposition**:
- Complex tasks are broken into manageable steps
- Each step is focused and achievable
- Dependencies ensure correct execution order
- Partial results are preserved and built upon

## The TaskDecomposer Service

```java
@Service
public class TaskDecomposer {
    private static final Logger log = LoggerFactory.getLogger(TaskDecomposer.class);

    private final ChatModel chatModel;

    public TaskDecomposer(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public TaskExecutionResult executeComplexTask(String complexTask) {
        log.info("Decomposing complex task: {}", complexTask);

        // Step 1: Decompose into subtasks
        List<Subtask> subtasks = decompose(complexTask);

        if (subtasks.isEmpty()) {
            return new TaskExecutionResult(
                    complexTask,
                    List.of(),
                    "Unable to decompose task into subtasks"
            );
        }

        log.info("Decomposed into {} subtasks", subtasks.size());

        // Step 2: Execute subtasks in dependency order
        List<SubtaskResult> results = new ArrayList<>();
        Map<String, String> completedResults = new HashMap<>();

        // Build execution order
        List<Subtask> executionOrder = topologicalSort(subtasks);

        for (Subtask subtask : executionOrder) {
            log.debug("Executing subtask {}: {}", subtask.id(), subtask.description());

            // Check dependencies are met
            boolean dependenciesMet = subtask.dependencies().stream()
                    .allMatch(completedResults::containsKey);

            if (!dependenciesMet) {
                String result = "Skipped: dependencies not met";
                results.add(new SubtaskResult(subtask.id(), subtask.description(), result, false));
                continue;
            }

            // Execute subtask
            String subtaskPrompt = buildSubtaskPrompt(subtask, completedResults);
            String result = chatModel.chat(subtaskPrompt);

            completedResults.put(subtask.id(), result);
            results.add(new SubtaskResult(subtask.id(), subtask.description(), result, true));

            log.debug("Completed subtask {}", subtask.id());
        }

        // Step 3: Synthesize results
        String summary = synthesizeResults(complexTask, results);

        return new TaskExecutionResult(complexTask, results, summary);
    }
}
```

### Three-Phase Execution

**Phase 1: Decomposition**
- Use LLM to generate 3-5 subtasks with dependencies
- Parse the structured response into `Subtask` objects

**Phase 2: Execution**
- Sort subtasks in topological order (dependencies first)
- Execute each subtask with access to completed dependency results
- Track completed results in a map

**Phase 3: Synthesis**
- Combine all subtask results into a final summary
- Use LLM to create a cohesive answer

## Decomposition: Breaking Down Tasks

```java
private List<Subtask> decompose(String task) {
    String decompositionPrompt = String.format("""
            Decompose this complex task into 3-5 sequential subtasks.

            Task: %s

            For each subtask, provide:
            - id: A unique identifier (e.g., "task1", "task2")
            - description: What needs to be done
            - dependencies: List of task IDs this depends on (empty if no dependencies)

            Format your response as:
            SUBTASK id=task1 deps=[]
            description: [description text]

            SUBTASK id=task2 deps=[task1]
            description: [description text]

            Keep it to 3-5 subtasks maximum.
            """, task);

    String response = chatModel.chat(decompositionPrompt);

    return parseSubtasks(response);
}
```

### Decomposition Prompt Design

**Clear Structure**: The prompt defines an exact format for the LLM to follow (`SUBTASK id=... deps=[...]`).

**Dependency Syntax**: Dependencies are listed as `deps=[task1,task2]` for easy parsing.

**Limit Scope**: "3-5 subtasks maximum" prevents overly complex plans.

**Example**: For "Research top customers and their issues", the LLM might generate:
```
SUBTASK id=task1 deps=[]
description: Query database to identify top 10 customers by revenue

SUBTASK id=task2 deps=[task1]
description: For each top customer, retrieve their open support tickets

SUBTASK id=task3 deps=[task2]
description: Analyze tickets to identify common issues and patterns
```

## Parsing Subtasks

```java
private List<Subtask> parseSubtasks(String response) {
    List<Subtask> subtasks = new ArrayList<>();

    String[] blocks = response.split("SUBTASK");

    for (String block : blocks) {
        if (block.trim().isEmpty()) continue;

        try {
            // Parse id
            String id = extractPattern(block, "id=(\\w+)");

            // Parse dependencies
            String depsStr = extractPattern(block, "deps=\\[([^\\]]*)\\]");
            List<String> deps = depsStr.isEmpty()
                    ? List.of()
                    : Arrays.stream(depsStr.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());

            // Parse description
            String description = extractPattern(block, "description:\\s*(.+?)(?=SUBTASK|$)");

            if (!id.isEmpty() && !description.isEmpty()) {
                subtasks.add(new Subtask(id, description.trim(), deps));
            }
        } catch (Exception e) {
            log.warn("Failed to parse subtask block: {}", block, e);
        }
    }

    return subtasks;
}

private String extractPattern(String text, String pattern) {
    java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.DOTALL);
    java.util.regex.Matcher m = p.matcher(text);
    return m.find() ? m.group(1).trim() : "";
}
```

### Parsing Strategy

**Split on SUBTASK**: Each subtask block starts with "SUBTASK".

**Extract Fields**: Use regex to pull out `id`, `deps`, and `description`.

**Dependency Parsing**: Split the dependency list on commas, trim whitespace, filter empties.

**Error Tolerance**: If a subtask block fails to parse, log a warning and continue (prevents one malformed subtask from breaking the entire plan).

## Topological Sorting

To execute subtasks in the correct order, we use topological sort:

```java
private List<Subtask> topologicalSort(List<Subtask> subtasks) {
    List<Subtask> sorted = new ArrayList<>();
    Set<String> visited = new HashSet<>();

    for (Subtask task : subtasks) {
        visit(task, subtasks, visited, sorted);
    }

    return sorted;
}

private void visit(Subtask task, List<Subtask> all, Set<String> visited, List<Subtask> sorted) {
    if (visited.contains(task.id())) return;

    // Visit dependencies first
    for (String depId : task.dependencies()) {
        all.stream()
                .filter(t -> t.id().equals(depId))
                .findFirst()
                .ifPresent(dep -> visit(dep, all, visited, sorted));
    }

    visited.add(task.id());
    sorted.add(task);
}
```

### How Topological Sort Works

**Graph Traversal**: We treat subtasks as nodes in a directed acyclic graph (DAG).

**Dependencies First**: For each task, we recursively visit its dependencies before adding the task itself.

**Visited Set**: Prevents infinite loops and duplicate visits.

**Example**: Given:
```
task1: no dependencies
task2: depends on task1
task3: depends on task2
```

**Sorted Output**: `[task1, task2, task3]`

## Executing Subtasks with Context

```java
private String buildSubtaskPrompt(Subtask subtask, Map<String, String> completedResults) {
    StringBuilder prompt = new StringBuilder();
    prompt.append("Execute this subtask:\n\n");
    prompt.append(subtask.description()).append("\n\n");

    if (!subtask.dependencies().isEmpty()) {
        prompt.append("Previous results from dependencies:\n");
        for (String depId : subtask.dependencies()) {
            String result = completedResults.get(depId);
            if (result != null) {
                prompt.append(String.format("- %s: %s\n", depId, result));
            }
        }
    }

    prompt.append("\nProvide a concise result for this subtask.");
    return prompt.toString();
}
```

### Subtask Execution Context

**Include Dependencies**: The prompt provides results from all completed dependencies, giving the LLM context.

**Example Prompt**:
```
Execute this subtask:

For each top customer, retrieve their open support tickets

Previous results from dependencies:
- task1: Top customers are: ACME Corp (ID: 100), TechStart (ID: 200), Global Inc (ID: 300)

Provide a concise result for this subtask.
```

**LLM Response**: "ACME Corp has 5 open tickets, TechStart has 2 open tickets, Global Inc has 8 open tickets."

This result is then passed to task3, which can analyze the ticket counts.

## Synthesizing Final Results

```java
private String synthesizeResults(String originalTask, List<SubtaskResult> results) {
    String synthesisPrompt = String.format("""
            Original task: %s

            Subtask results:
            %s

            Provide a comprehensive summary combining all subtask results.
            """,
            originalTask,
            results.stream()
                    .map(r -> String.format("%s: %s", r.subtaskId(), r.result()))
                    .collect(Collectors.joining("\n"))
    );

    return chatModel.chat(synthesisPrompt);
}
```

### Synthesis Purpose

**Coherent Answer**: Rather than returning a list of subtask results, we synthesize them into a single, coherent answer.

**Context Preservation**: The synthesis prompt includes the original task, ensuring the summary addresses the user's original question.

**Example**:
- Original task: "Research top customers and their common issues"
- Subtask results: (task1 identified customers, task2 got tickets, task3 analyzed patterns)
- **Synthesized answer**: "Our top customers (ACME Corp, TechStart, Global Inc) have 15 open tickets. Common issues include: login failures (40%), slow performance (35%), and integration errors (25%). Recommend prioritizing authentication improvements."

## Data Structures

The decomposer uses three record types:

```java
public record Subtask(
        String id,
        String description,
        List<String> dependencies
) {}

public record SubtaskResult(
        String subtaskId,
        String description,
        String result,
        boolean success
) {}

public record TaskExecutionResult(
        String originalTask,
        List<SubtaskResult> subtaskResults,
        String summary
) {}
```

These immutable records make the data flow clear and type-safe.

## Example: Full Execution

**User Input**: "Analyze customer satisfaction for our Enterprise customers"

**Decomposition**:
```
task1: Identify all customers on Enterprise plan
task2 (depends on task1): For each Enterprise customer, calculate average ticket resolution time
task3 (depends on task2): Analyze resolution times to determine satisfaction levels
```

**Execution**:

*Task1*:
- Prompt: "Identify all customers on Enterprise plan"
- Result: "Enterprise customers: ACME (ID: 100), TechStart (ID: 200)"

*Task2*:
- Prompt: "For each Enterprise customer, calculate average ticket resolution time. Previous results: ACME (ID: 100), TechStart (ID: 200)"
- Result: "ACME: 12 hours average, TechStart: 8 hours average"

*Task3*:
- Prompt: "Analyze resolution times to determine satisfaction levels. Previous results: ACME: 12 hours, TechStart: 8 hours"
- Result: "TechStart shows high satisfaction (8hr resolution), ACME shows moderate satisfaction (12hr resolution)"

**Synthesis**: "Enterprise customer satisfaction analysis: We have 2 Enterprise customers. TechStart has excellent support response (8hr avg), while ACME has room for improvement (12hr avg). Overall Enterprise satisfaction is good, with opportunities to further reduce ACME's resolution time."

## Best Practices

### Decomposition Prompts

- **Limit Subtasks**: 3-5 is ideal; too many subtasks become unwieldy
- **Clear Format**: Define exact output format for easier parsing
- **Dependency Syntax**: Use simple lists like `deps=[task1,task2]`

### Execution

- **Topological Sort**: Always sort before executing to respect dependencies
- **Pass Context**: Include dependency results in subtask prompts
- **Track Progress**: Log each subtask execution for debugging

### Error Handling

- **Skip on Unmet Dependencies**: If a dependency failed, skip the dependent task
- **Partial Results**: Return what was completed even if some tasks fail
- **Validate DAG**: Ensure no circular dependencies (would cause infinite loop)

### Performance

- **Parallel Execution**: Independent subtasks (no shared dependencies) can run in parallel
- **Cache Results**: Don't re-execute subtasks if called multiple times
- **Limit Depth**: Prevent overly nested decompositions

## Advanced: Parallel Execution

Tasks with no shared dependencies can run in parallel:

```java
// Group subtasks by dependency level
Map<Integer, List<Subtask>> levels = groupByDependencyLevel(subtasks);

for (Map.Entry<Integer, List<Subtask>> entry : levels.entrySet()) {
    List<Subtask> levelTasks = entry.getValue();

    // Execute all tasks at this level in parallel
    List<CompletableFuture<SubtaskResult>> futures = levelTasks.stream()
            .map(subtask -> CompletableFuture.supplyAsync(() -> {
                String prompt = buildSubtaskPrompt(subtask, completedResults);
                String result = chatModel.chat(prompt);
                return new SubtaskResult(subtask.id(), subtask.description(), result, true);
            }))
            .toList();

    // Wait for all to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    // Store results
    for (CompletableFuture<SubtaskResult> future : futures) {
        SubtaskResult result = future.get();
        completedResults.put(result.subtaskId(), result.result());
        results.add(result);
    }
}
```

This reduces total execution time from `sum(task_times)` to `max(level_times)`.

## Testing

```java
@Test
void decompose_CreatesValidSubtasks() {
    String task = "Research our top customers and their issues";
    List<Subtask> subtasks = taskDecomposer.decompose(task);

    assertThat(subtasks).hasSizeGreaterThanOrEqualTo(3);
    assertThat(subtasks.get(0).dependencies()).isEmpty(); // First task has no deps
}

@Test
void topologicalSort_RespectsDepenencies() {
    List<Subtask> subtasks = List.of(
        new Subtask("task1", "First", List.of()),
        new Subtask("task2", "Second", List.of("task1")),
        new Subtask("task3", "Third", List.of("task2"))
    );

    List<Subtask> sorted = taskDecomposer.topologicalSort(subtasks);

    assertThat(sorted.get(0).id()).isEqualTo("task1");
    assertThat(sorted.get(1).id()).isEqualTo("task2");
    assertThat(sorted.get(2).id()).isEqualTo("task3");
}
```

## Summary

Task decomposition enables agents to tackle complex, multi-step problems by:
- Breaking tasks into 3-5 manageable subtasks
- Tracking dependencies between subtasks
- Executing in topological order
- Passing results from dependencies to dependent tasks
- Synthesizing final answers from all subtask results

Key concepts:
- `Subtask`: Represents a single step with ID, description, and dependencies
- Decomposition: LLM generates structured plan
- Topological sort: Ensures correct execution order
- Context passing: Dependent tasks receive results from prerequisites
- Synthesis: Combines subtask results into cohesive answer

In the next chapter, we'll explore **agent tools**—how to give agents access to databases, APIs, and external systems.

---

**Next Chapter**: [07 - Agent Tools](./07-agent-tools.md)
