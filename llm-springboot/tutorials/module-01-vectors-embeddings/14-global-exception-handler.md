# Chapter: GlobalExceptionHandler - Error Handling

## Introduction

**GlobalExceptionHandler** provides centralized error handling for the REST API, ensuring consistent error responses.

## Code

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        var errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("field", error.getField(), "message", (Object) error.getDefaultMessage()))
                .toList();

        return ResponseEntity.badRequest().body(Map.of("error", "Validation failed", "details", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed request body"));
    }
}
```

## Key Points

- **@RestControllerAdvice** applies globally to all controllers
- **Handles validation errors** with field-level details
- **Handles malformed JSON** with clear error messages
- **Returns HTTP 400** for client errors
- **Consistent error format** across the API

## Tutorial Complete!

You've learned how to build a complete semantic vector search system with Spring Boot! The system loads documents, chunks them, generates embeddings, builds indexes, and provides fast similarity-based search through a REST API.
