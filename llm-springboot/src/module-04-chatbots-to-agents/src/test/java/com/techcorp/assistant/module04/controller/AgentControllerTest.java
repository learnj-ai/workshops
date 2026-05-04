package com.techcorp.assistant.module04.controller;

import com.techcorp.assistant.module04.agent.ReActAgent;
import com.techcorp.assistant.module04.memory.ConversationMemoryService;
import com.techcorp.assistant.module04.orchestrator.MultiAgentOrchestrator;
import com.techcorp.assistant.module04.service.TaskDecomposer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AgentController.
 */
@WebMvcTest(AgentController.class)
class AgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReActAgent reActAgent;

    @MockBean
    private MultiAgentOrchestrator multiAgentOrchestrator;

    @MockBean
    private TaskDecomposer taskDecomposer;

    @MockBean
    private ConversationMemoryService memoryService;

    @Test
    void testHealthEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/agent/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("Module 04: Agents - OK"));
    }

    @Test
    void testExecuteEndpoint_ReActMode() throws Exception {
        // Given
        when(reActAgent.solve(anyString())).thenReturn("Agent response");

        // When/Then
        mockMvc.perform(post("/api/v1/agent/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "message": "Test question",
                                    "mode": "react"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").value("Agent response"))
                .andExpect(jsonPath("$.mode").value("react"));
    }

    @Test
    void testClearSession() throws Exception {
        mockMvc.perform(delete("/api/v1/agent/session/test-session"))
                .andExpect(status().isOk())
                .andExpect(content().string("Session cleared"));
    }
}
