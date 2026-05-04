package com.techcorp.assistant.module06.dokimos;

import dev.dokimos.core.EvalResult;
import dev.dokimos.core.EvalTestCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ResponseLengthEvaluator.
 */
@DisplayName("ResponseLengthEvaluator Tests")
class ResponseLengthEvaluatorTest {

    private ResponseLengthEvaluator evaluator;

    @BeforeEach
    void setUp() {
        // Create evaluator with min=50, max=500, threshold=0.8
        evaluator = new ResponseLengthEvaluator(50, 500, 0.8);
    }

    @Test
    @DisplayName("Should pass for response within length bounds")
    void testResponseWithinBounds() {
        // Create test case with response of valid length
        String response = "This is a valid response with appropriate length that falls within the configured bounds.";
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of("output", response))
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify success
        assertNotNull(result);
        assertEquals("response-length", result.name());
        assertEquals(1.0, result.score());
        assertTrue(result.success());
    }

    @Test
    @DisplayName("Should fail for response too short")
    void testResponseTooShort() {
        // Create test case with short response
        String response = "Too short";
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of("output", response))
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify failure
        assertNotNull(result);
        assertEquals("response-length", result.name());
        assertEquals(0.0, result.score());
        assertFalse(result.success());
        assertTrue(result.reason().contains("too short"));
    }

    @Test
    @DisplayName("Should fail for response too long")
    void testResponseTooLong() {
        // Create test case with long response
        String response = "x".repeat(600);  // 600 characters
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of("output", response))
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify failure
        assertNotNull(result);
        assertEquals("response-length", result.name());
        assertEquals(0.0, result.score());
        assertFalse(result.success());
        assertTrue(result.reason().contains("too long"));
    }

    @Test
    @DisplayName("Should handle missing output field")
    void testMissingOutput() {
        // Create test case without output field
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of())
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify failure with appropriate reason
        assertNotNull(result);
        assertEquals("response-length", result.name());
        assertEquals(0.0, result.score());
        assertFalse(result.success());
        assertTrue(result.reason().contains("missing") || result.reason().contains("null"));
    }

    @Test
    @DisplayName("Should handle non-string output")
    void testNonStringOutput() {
        // Create test case with non-string output
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of("output", 12345))
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify failure with appropriate reason
        assertNotNull(result);
        assertEquals(0.0, result.score());
        assertFalse(result.success());
    }

    @Test
    @DisplayName("Should accept response at minimum length boundary")
    void testMinimumBoundary() {
        // Create response exactly at minimum length (50 chars)
        String response = "x".repeat(50);
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of("output", response))
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify success
        assertEquals(1.0, result.score());
        assertTrue(result.success());
    }

    @Test
    @DisplayName("Should accept response at maximum length boundary")
    void testMaximumBoundary() {
        // Create response exactly at maximum length (500 chars)
        String response = "x".repeat(500);
        EvalTestCase testCase = EvalTestCase.builder()
                .actualOutputs(Map.of("output", response))
                .build();

        // Evaluate
        EvalResult result = evaluator.runEvaluation(testCase);

        // Verify success
        assertEquals(1.0, result.score());
        assertTrue(result.success());
    }
}
