package com.techcorp.assistant.rag;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RAGControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Stub RAGService that returns a fixed answer
        RAGService ragService = new RAGService(null, null, null) {
            @Override
            public String query(String userQuestion, boolean useQueryExpansion) {
                return "You can reset your password from the identity portal.";
            }
        };

        // Stub HybridSearchService (not used by /query endpoint tests)
        HybridSearchService hybridSearchService = new HybridSearchService(null, null, null);

        RAGController controller = new RAGController(ragService, hybridSearchService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void queryReturnsAnswer() throws Exception {
        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content("""
                                {"question": "How do I reset my password?"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("You can reset your password from the identity portal."));
    }

    @Test
    void queryRejectsMissingQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void queryRejectsMalformedBody() throws Exception {
        mockMvc.perform(post("/api/v1/rag/query")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content("not json"))
                .andExpect(status().isBadRequest());
    }
}
