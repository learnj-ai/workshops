package com.techcorp.assistant.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Complete RAG pipeline: query transformation -> retrieval -> answer generation.
 * <p>
 * Pipeline stages:
 * 1. Expand the query using multi-query transformation (optional, improves recall)
 * 2. Generate a HyDE document for vector-only retrieval (optional, improves semantic recall)
 * 3. Retrieve candidates via hybrid search for each query variant
 * 4. Deduplicate and take top K segments
 * 5. Build context from retrieved segments
 * 6. Generate an answer grounded in the retrieved context
 */
@Service
public class RAGService {

    private static final Logger log = LoggerFactory.getLogger(RAGService.class);
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_CONTEXT_SEGMENTS = 10;

    private final HybridSearchService searchService;
    private final ChatModel llm;
    private final QueryTransformer queryTransformer;

    public RAGService(HybridSearchService searchService, ChatModel llm, QueryTransformer queryTransformer) {
        this.searchService = searchService;
        this.llm = llm;
        this.queryTransformer = queryTransformer;
    }

    public String query(String userQuestion) {
        return query(userQuestion, true);
    }

    public String query(String userQuestion, boolean useQueryExpansion) {
        long pipelineStart = System.currentTimeMillis();
        log.info("╔══ RAG Pipeline Start ══════════════════════════════════════");
        log.info("║ Question: {}", userQuestion);
        log.info("║ Query expansion: {}", useQueryExpansion ? "ON" : "OFF");

        // Step 1: Query transformation
        List<String> queries = new ArrayList<>();
        queries.add(userQuestion);
        String hypotheticalDocument = null;

        if (useQueryExpansion) {
            long transformStart = System.currentTimeMillis();
            List<String> alternatives = queryTransformer.multiQuery(userQuestion);
            queries.addAll(alternatives);
            hypotheticalDocument = queryTransformer.generateHypotheticalDocument(userQuestion);
            long transformElapsed = System.currentTimeMillis() - transformStart;

            log.info("╠══ Step 1: Query Transformation ({}ms) ═════════════════", transformElapsed);
            log.info("║ Original: {}", userQuestion);
            for (int i = 0; i < alternatives.size(); i++) {
                log.info("║ Alt[{}]:   {}", i + 1, alternatives.get(i));
            }
            if (shouldUseHyde(hypotheticalDocument, userQuestion)) {
                log.info("║ HyDE:     {} ...", truncate(hypotheticalDocument, 100));
            }
        }

        // Step 2: Retrieve from multiple queries via hybrid search
        long retrievalStart = System.currentTimeMillis();
        List<TextSegment> allResults = new ArrayList<>();
        for (String query : queries) {
            List<TextSegment> results = searchService.hybridSearch(query, DEFAULT_TOP_K);
            log.info("║ Hybrid search for '{}' → {} results", truncate(query, 60), results.size());
            allResults.addAll(results);
        }

        // HyDE works best as semantic retrieval input, not lexical keyword search input.
        if (useQueryExpansion && shouldUseHyde(hypotheticalDocument, userQuestion)) {
            List<TextSegment> hydeResults = searchService.vectorOnlySearch(hypotheticalDocument, DEFAULT_TOP_K);
            log.info("║ HyDE vector search → {} results", hydeResults.size());
            allResults.addAll(hydeResults);
        }
        long retrievalElapsed = System.currentTimeMillis() - retrievalStart;
        log.info("╠══ Step 2: Retrieval ({}ms) — {} total candidates ══════════", retrievalElapsed, allResults.size());

        // Step 3: Deduplicate and take top K
        List<TextSegment> topResults = deduplicate(allResults, MAX_CONTEXT_SEGMENTS);
        log.info("╠══ Step 3: Deduplication — {} → {} unique segments ═════════", allResults.size(), topResults.size());
        for (int i = 0; i < topResults.size(); i++) {
            log.info("║ [{}] {} ...", i + 1, truncate(topResults.get(i).text(), 80));
        }

        if (topResults.isEmpty()) {
            log.info("╚══ RAG Pipeline End — no relevant context found ═════════");
            return "I don't have enough information to answer that question.";
        }

        // Step 4: Build context
        String context = topResults.stream()
                .map(TextSegment::text)
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        log.info("╠══ Step 4: Context — {} chars from {} segments ═════════════", context.length(), topResults.size());

        // Step 5: Generate answer
        long llmStart = System.currentTimeMillis();
        String prompt = """
                You are TechCorp's AI assistant. Answer the user's question based
                strictly on the provided context. If the context doesn't contain
                the answer, say "I don't have enough information to answer that question."

                Context:
                %s

                Question: %s

                Answer:
                """.formatted(context, userQuestion);

        String answer = llm.chat(prompt);
        long llmElapsed = System.currentTimeMillis() - llmStart;

        long totalElapsed = System.currentTimeMillis() - pipelineStart;
        log.info("╠══ Step 5: LLM Generation ({}ms) ══════════════════════════", llmElapsed);
        log.info("║ Answer: {}", truncate(answer, 200));
        log.info("╚══ RAG Pipeline End — total {}ms ══════════════════════════", totalElapsed);
        return answer;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= maxLength ? cleaned : cleaned.substring(0, maxLength);
    }

    private boolean shouldUseHyde(String hypotheticalDocument, String userQuestion) {
        if (hypotheticalDocument == null || hypotheticalDocument.isBlank()) {
            return false;
        }
        return !hypotheticalDocument.trim().equalsIgnoreCase(userQuestion.trim());
    }

    private List<TextSegment> deduplicate(List<TextSegment> segments, int maxResults) {
        Set<String> seen = new LinkedHashSet<>();
        List<TextSegment> unique = new ArrayList<>();

        for (TextSegment segment : segments) {
            if (seen.add(segment.text()) && unique.size() < maxResults) {
                unique.add(segment);
            }
        }

        return unique;
    }
}
