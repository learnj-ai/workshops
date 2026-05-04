package com.techcorp.assistant.module03.controller;

import com.techcorp.assistant.module03.dto.ChatRequest;
import com.techcorp.assistant.module03.dto.ChatResponse;
import com.techcorp.assistant.module03.service.ToolOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AssistantController.
 */
@WebMvcTest(AssistantController.class)
class AssistantControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ToolOrchestrator toolOrchestrator;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/assistant/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Module 03: Tools & MCP - OK"));
    }

    @Test
    void testChatEndpoint_Success() throws Exception {
        // Given
        when(toolOrchestrator.processRequest(anyString()))
                .thenReturn("Customer Alice Johnson has email alice.johnson@example.com");

        // When/Then
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "message": "What is customer 12345's email?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Customer Alice Johnson has email alice.johnson@example.com"));
    }

    @Test
    void testChatEndpoint_EmptyMessage() throws Exception {
        // When/Then
        mockMvc.perform(post("/api/v1/assistant/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "message": ""
                                }
                                """))
                .andExpect(status().is4xxClientError());
    }
}
