package com.techcorp.assistant.rag;

import com.techcorp.assistant.chunking.ChunkingStrategy;
import com.techcorp.assistant.store.IndexedSegment;
import com.techcorp.assistant.store.VectorStoreService;
import dev.langchain4j.data.segment.TextSegment;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * BM25-inspired keyword search using TF-IDF scoring.
 * Students compare this with vector search to understand why hybrid search matters:
 * keyword search excels at exact term matching, while vector search captures semantic meaning.
 */
@Service
public class KeywordSearchService {

    private static final double BM25_K1 = 1.2;
    private static final double BM25_B = 0.75;

    private final Supplier<List<IndexedSegment>> segmentSupplier;

    @Autowired
    public KeywordSearchService(VectorStoreService vectorStoreService) {
        this.segmentSupplier = () -> vectorStoreService.getAllSegments(ChunkingStrategy.RECURSIVE);
    }

    KeywordSearchService(List<IndexedSegment> fixedSegments) {
        this.segmentSupplier = () -> fixedSegments;
    }

    public List<TextSegment> search(String query, int maxResults) {
        List<IndexedSegment> allSegments = segmentSupplier.get();
        if (allSegments.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = tokenize(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        double avgDocLength = allSegments.stream()
                .mapToInt(seg -> tokenize(seg.segment().text()).size())
                .average()
                .orElse(1.0);

        int totalDocs = allSegments.size();

        // Precompute document frequencies for each query term
        Map<String, Integer> documentFrequencies = new HashMap<>();
        for (String term : queryTerms) {
            int df = (int) allSegments.stream()
                    .filter(seg -> tokenize(seg.segment().text()).contains(term))
                    .count();
            documentFrequencies.put(term, df);
        }

        record ScoredSegment(TextSegment segment, double score) {}

        return allSegments.stream()
                .map(indexed -> {
                    double score = computeBM25Score(
                            indexed.segment().text(), queryTerms,
                            documentFrequencies, totalDocs, avgDocLength);
                    return new ScoredSegment(indexed.segment(), score);
                })
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingDouble(ScoredSegment::score).reversed())
                .limit(maxResults)
                .map(ScoredSegment::segment)
                .toList();
    }

    private double computeBM25Score(
            String documentText,
            Set<String> queryTerms,
            Map<String, Integer> documentFrequencies,
            int totalDocs,
            double avgDocLength) {

        Set<String> docTerms = tokenize(documentText);
        int docLength = docTerms.size();
        Map<String, Long> termFrequencies = countTermFrequencies(documentText);

        double score = 0.0;
        for (String term : queryTerms) {
            int df = documentFrequencies.getOrDefault(term, 0);
            if (df == 0) {
                continue;
            }

            // IDF component: log((N - df + 0.5) / (df + 0.5) + 1)
            double idf = Math.log((totalDocs - df + 0.5) / (df + 0.5) + 1.0);

            // TF component with BM25 saturation
            long tf = termFrequencies.getOrDefault(term, 0L);
            double tfNormalized = (tf * (BM25_K1 + 1.0))
                    / (tf + BM25_K1 * (1.0 - BM25_B + BM25_B * docLength / avgDocLength));

            score += idf * tfNormalized;
        }

        return score;
    }

    private Map<String, Long> countTermFrequencies(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(t -> !t.isEmpty())
                .collect(Collectors.groupingBy(t -> t, Collectors.counting()));
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(t -> !t.isEmpty() && t.length() > 1)
                .collect(Collectors.toSet());
    }
}
