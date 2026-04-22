package com.techcorp.assistant.rag;

import com.techcorp.assistant.store.VectorStoreService;
import dev.langchain4j.data.segment.TextSegment;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Combines vector search (semantic) with keyword search (lexical) using
 * Reciprocal Rank Fusion (RRF) to merge ranked lists from different retrieval methods.
 * <p>
 * Why hybrid? Vector search captures meaning ("how do I connect remotely" → VPN docs)
 * but misses exact terms. Keyword search finds exact matches ("SEV1" → incident docs)
 * but misses paraphrases. Hybrid search gets the best of both.
 */
@Service
public class HybridSearchService {

    private static final Logger log = LoggerFactory.getLogger(HybridSearchService.class);
    private static final int RRF_RANK_CONSTANT = 60;

    private final VectorStoreService vectorStore;
    private final KeywordSearchService keywordSearch;
    private final ReRanker reRanker;

    public HybridSearchService(
            VectorStoreService vectorStore,
            KeywordSearchService keywordSearch,
            ReRanker reRanker) {
        this.vectorStore = vectorStore;
        this.keywordSearch = keywordSearch;
        this.reRanker = reRanker;
    }

    public List<TextSegment> hybridSearch(String query, int topK) {
        int retrievalSize = topK * 2;

        // Stage 1: Parallel retrieval from both sources
        List<TextSegment> vectorResults = vectorStore.searchSegments(query, retrievalSize);
        List<TextSegment> keywordResults = keywordSearch.search(query, retrievalSize);

        log.debug("Vector search returned {} results, keyword search returned {} results",
                vectorResults.size(), keywordResults.size());

        // Stage 2: Merge results using Reciprocal Rank Fusion
        List<TextSegment> mergedResults = reciprocalRankFusion(
                vectorResults, keywordResults, retrievalSize);

        log.debug("RRF merged to {} results", mergedResults.size());

        // Stage 3: Re-rank with the re-ranker
        return reRanker.rerank(query, mergedResults, topK);
    }

    public List<TextSegment> vectorOnlySearch(String query, int topK) {
        return vectorStore.searchSegments(query, topK);
    }

    public List<TextSegment> keywordOnlySearch(String query, int topK) {
        return keywordSearch.search(query, topK);
    }

    List<TextSegment> reciprocalRankFusion(
            List<TextSegment> list1,
            List<TextSegment> list2,
            int maxResults) {

        Map<String, Double> scores = new HashMap<>();
        Map<String, TextSegment> segmentsByText = new HashMap<>();

        // Score from list 1
        for (int i = 0; i < list1.size(); i++) {
            TextSegment segment = list1.get(i);
            String text = segment.text();
            scores.merge(text, 1.0 / (RRF_RANK_CONSTANT + i + 1), Double::sum);
            segmentsByText.putIfAbsent(text, segment);
        }

        // Score from list 2
        for (int i = 0; i < list2.size(); i++) {
            TextSegment segment = list2.get(i);
            String text = segment.text();
            scores.merge(text, 1.0 / (RRF_RANK_CONSTANT + i + 1), Double::sum);
            segmentsByText.putIfAbsent(text, segment);
        }

        // Sort by combined RRF score
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(maxResults)
                .map(entry -> segmentsByText.get(entry.getKey()))
                .filter(Objects::nonNull)
                .toList();
    }
}
