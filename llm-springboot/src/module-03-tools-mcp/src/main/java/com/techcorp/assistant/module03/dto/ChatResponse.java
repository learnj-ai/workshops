package com.techcorp.assistant.module03.dto;

/**
 * Response DTO for chat endpoint.
 *
 * @param response The AI-generated response (potentially tool-augmented)
 * @param toolsUsed Names of tools that were invoked during processing
 */
public record ChatResponse(String response, java.util.List<String> toolsUsed) {
    public ChatResponse(String response) {
        this(response, java.util.List.of());
    }
}
