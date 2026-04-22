package com.techcorp.assistant.rag;

import static org.assertj.core.api.Assertions.assertThat;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RAGServiceTest {

    @Test
    void queryUsesHydeDocumentForAdditionalVectorRetrieval() {
        RecordingHybridSearchService searchService = new RecordingHybridSearchService(
                List.of(TextSegment.from("Reset passwords via the identity portal.")),
                List.of(TextSegment.from("The identity portal supports self-service password reset."))
        );
        StubQueryTransformer queryTransformer = new StubQueryTransformer(
                List.of("Password reset steps"),
                "Employees can reset passwords from the identity portal."
        );
        RAGService ragService = new RAGService(searchService, new StubChatModel("Use the identity portal."), queryTransformer);

        String answer = ragService.query("How do I reset my password?", true);

        assertThat(answer).isEqualTo("Use the identity portal.");
        assertThat(searchService.hybridQueries).containsExactly(
                "How do I reset my password?",
                "Password reset steps");
        assertThat(searchService.vectorQueries)
                .containsExactly("Employees can reset passwords from the identity portal.");
    }

    @Test
    void querySkipsHydeVectorRetrievalWhenFallbackMatchesOriginalQuery() {
        RecordingHybridSearchService searchService = new RecordingHybridSearchService(
                List.of(TextSegment.from("Reset passwords via the identity portal.")),
                List.of()
        );
        StubQueryTransformer queryTransformer = new StubQueryTransformer(
                List.of(),
                "How do I reset my password?"
        );
        RAGService ragService = new RAGService(searchService, new StubChatModel("Use the identity portal."), queryTransformer);

        String answer = ragService.query("How do I reset my password?", true);

        assertThat(answer).isEqualTo("Use the identity portal.");
        assertThat(searchService.hybridQueries).containsExactly("How do I reset my password?");
        assertThat(searchService.vectorQueries).isEmpty();
    }

    private static final class RecordingHybridSearchService extends HybridSearchService {

        private final List<TextSegment> hybridResults;
        private final List<TextSegment> vectorResults;
        private final List<String> hybridQueries = new ArrayList<>();
        private final List<String> vectorQueries = new ArrayList<>();

        private RecordingHybridSearchService(List<TextSegment> hybridResults, List<TextSegment> vectorResults) {
            super(null, null, null);
            this.hybridResults = hybridResults;
            this.vectorResults = vectorResults;
        }

        @Override
        public List<TextSegment> hybridSearch(String query, int topK) {
            hybridQueries.add(query);
            return hybridResults;
        }

        @Override
        public List<TextSegment> vectorOnlySearch(String query, int topK) {
            vectorQueries.add(query);
            return vectorResults;
        }
    }

    private static final class StubQueryTransformer extends QueryTransformer {

        private final List<String> alternatives;
        private final String hypotheticalDocument;

        private StubQueryTransformer(List<String> alternatives, String hypotheticalDocument) {
            super(new StubChatModel(""));
            this.alternatives = alternatives;
            this.hypotheticalDocument = hypotheticalDocument;
        }

        @Override
        public List<String> multiQuery(String originalQuery) {
            return alternatives;
        }

        @Override
        public String generateHypotheticalDocument(String query) {
            return hypotheticalDocument;
        }
    }

    private static final class StubChatModel implements ChatModel {

        private final String response;

        private StubChatModel(String response) {
            this.response = response;
        }

        @Override
        public String chat(String userMessage) {
            return response;
        }
    }
}
