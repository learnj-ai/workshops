package com.techcorp.assistant.search;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.techcorp.assistant.Module01VectorsEmbeddingsApplication;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = {
        Module01VectorsEmbeddingsApplication.class,
        VectorSearchControllerTest.TestEmbeddingConfiguration.class
})
@AutoConfigureMockMvc
class VectorSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnRelevantVectorMatches() throws Exception {
        mockMvc.perform(post("/api/v1/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "How do I reset my password?",
                                  "maxResults": 2,
                                  "metric": "COSINE",
                                  "chunkingStrategy": "RECURSIVE"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingDimension").value(4))
                .andExpect(jsonPath("$.metric").value("COSINE"))
                .andExpect(jsonPath("$.results[0].metadata.source").value("password-reset.md"))
                .andExpect(jsonPath("$.results[0].content").value(org.hamcrest.Matchers.containsString("reset")));
    }

    @Test
    void shouldReturnBadRequestForBlankQuery() throws Exception {
        mockMvc.perform(post("/api/v1/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "  "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation failed"));
    }

    @Test
    void shouldReturnBadRequestForInvalidEnum() throws Exception {
        mockMvc.perform(post("/api/v1/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "test",
                                  "metric": "INVALID"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Malformed request body"));
    }

    @Test
    void shouldApplyDefaultsWhenOptionalFieldsOmitted() throws Exception {
        mockMvc.perform(post("/api/v1/search/vector")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "vpn access"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metric").value("COSINE"))
                .andExpect(jsonPath("$.chunkingStrategy").value("RECURSIVE"))
                .andExpect(jsonPath("$.results").isArray());
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestEmbeddingConfiguration {

        @Bean
        @Primary
        EmbeddingModel testEmbeddingModel() {
            return new KeywordEmbeddingModel();
        }
    }

    static class KeywordEmbeddingModel implements EmbeddingModel {

        @Override
        public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
            List<Embedding> embeddings = segments.stream()
                    .map(segment -> Embedding.from(vectorize(segment.text())))
                    .toList();
            return Response.from(embeddings);
        }

        @Override
        public int dimension() {
            return 4;
        }

        private float[] vectorize(String text) {
            String normalized = text.toLowerCase();
            return new float[]{
                    score(normalized, "password", "reset", "identity"),
                    score(normalized, "vpn", "token", "remote"),
                    score(normalized, "api", "requests", "429"),
                    score(normalized, "ticket", "operations", "support")
            };
        }

        private float score(String text, String... terms) {
            float score = 0.0f;
            for (String term : terms) {
                if (text.contains(term)) {
                    score += 1.0f;
                }
            }
            return score;
        }
    }
}
