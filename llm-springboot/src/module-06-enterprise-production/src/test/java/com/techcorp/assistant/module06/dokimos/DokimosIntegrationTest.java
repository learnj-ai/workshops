package com.techcorp.assistant.module06.dokimos;

import dev.dokimos.core.ExperimentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for full Dokimos experiment execution.
 * Tests end-to-end workflow from dataset loading to result aggregation.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "evaluation.dataset.path=src/main/resources/data/eval-golden-set.json",
        "spring.ai.openai.api-key=${OPENAI_API_KEY:test-key}",
        "dokimos.judge.model=gpt-4",
        "dokimos.judge.temperature=0.0"
})
@DisplayName("Dokimos Integration Tests")
class DokimosIntegrationTest {

    @Autowired(required = false)
    private DokimosEvaluationService evaluationService;

    @Autowired(required = false)
    private DatasetLoader datasetLoader;

    @Test
    @DisplayName("Should load all required beans")
    void testBeansLoaded() {
        // Verify beans are loaded
        // Note: These may be null if API keys are not configured
        // In CI/CD, this test will be skipped if beans are not available
        if (System.getenv("OPENAI_API_KEY") != null) {
            assertNotNull(evaluationService, "DokimosEvaluationService should be loaded");
            assertNotNull(datasetLoader, "DatasetLoader should be loaded");
        }
    }

    @Test
    @DisplayName("Should run complete experiment workflow")
    void testCompleteExperimentWorkflow() throws Exception {
        // Skip test if no API key configured
        if (System.getenv("OPENAI_API_KEY") == null || evaluationService == null) {
            System.out.println("Skipping integration test - OPENAI_API_KEY not configured");
            return;
        }

        // Run experiment
        ExperimentResult result = evaluationService.runExperiment();

        // Verify experiment completed
        assertNotNull(result, "Experiment result should not be null");
        assertTrue(result.totalCount() > 0, "Should have processed examples");

        // Verify evaluators ran
        assertTrue(result.evaluatorNames().size() >= 3,
                "Should have multiple evaluators: " + result.evaluatorNames());

        // Verify result aggregation
        Map<String, Double> averages = evaluationService.extractAverages(result);
        assertNotNull(averages);
        assertFalse(averages.isEmpty());

        // Verify all scores are valid
        for (Map.Entry<String, Double> entry : averages.entrySet()) {
            double score = entry.getValue();
            assertTrue(Double.isFinite(score), "Score should be finite: " + entry.getKey());
            assertTrue(score >= 0.0 && score <= 1.0,
                    String.format("Score for %s (%.3f) should be in [0.0, 1.0]", entry.getKey(), score));
        }

        // Verify min/max extraction
        Map<String, double[]> minMax = evaluationService.extractMinMax(result);
        assertNotNull(minMax);
        for (Map.Entry<String, double[]> entry : minMax.entrySet()) {
            double[] range = entry.getValue();
            assertTrue(range[0] <= range[1],
                    String.format("Min %.3f should be <= max %.3f for %s", range[0], range[1], entry.getKey()));
        }
    }

    @Test
    @DisplayName("Should handle filtered evaluators")
    void testFilteredEvaluators() throws Exception {
        // Skip test if no API key configured
        if (System.getenv("OPENAI_API_KEY") == null || evaluationService == null) {
            System.out.println("Skipping integration test - OPENAI_API_KEY not configured");
            return;
        }

        // Run experiment with only response-length evaluator (doesn't require LLM)
        ExperimentResult result = evaluationService.runExperiment(
                java.util.List.of("response-length")
        );

        // Verify experiment ran with filtered evaluators
        assertNotNull(result);
        assertTrue(result.totalCount() > 0);

        // Verify only response-length evaluator ran
        assertTrue(result.evaluatorNames().contains("response-length"));
    }

    @Test
    @DisplayName("Should export results to JSON")
    void testJsonExport() throws Exception {
        // Skip test if no API key configured
        if (System.getenv("OPENAI_API_KEY") == null || evaluationService == null) {
            System.out.println("Skipping integration test - OPENAI_API_KEY not configured");
            return;
        }

        // Run experiment
        ExperimentResult result = evaluationService.runExperiment(
                java.util.List.of("response-length")
        );

        // Export to JSON
        String json = evaluationService.exportToJson(result);

        // Verify JSON contains expected fields
        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"totalCount\""));
    }

    @Test
    @DisplayName("Should format console output")
    void testConsoleFormatting() throws Exception {
        // Skip test if no API key configured
        if (System.getenv("OPENAI_API_KEY") == null || evaluationService == null) {
            System.out.println("Skipping integration test - OPENAI_API_KEY not configured");
            return;
        }

        // Run experiment
        ExperimentResult result = evaluationService.runExperiment(
                java.util.List.of("response-length")
        );

        // Format console output
        String formatted = evaluationService.formatForConsole(result);

        // Verify formatting
        assertNotNull(formatted);
        assertTrue(formatted.contains("EXPERIMENT RESULTS"));
        assertTrue(formatted.contains("Total Examples"));
        assertTrue(formatted.contains("Pass Rate"));
    }
}
