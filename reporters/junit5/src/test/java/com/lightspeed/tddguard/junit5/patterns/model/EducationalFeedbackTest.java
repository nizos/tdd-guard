package com.lightspeed.tddguard.junit5.patterns.model;

import org.junit.jupiter.api.Test;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for EducationalFeedback model.
 * Validates JSON serialization and data structure.
 */
class EducationalFeedbackTest {

    private final Gson gson = new Gson();

    @Test
    void shouldCreateWithAllFields() {
        // Given
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("mockCount", 47);
        evidence.put("testCount", 23);
        evidence.put("ratio", "2.04");

        // When
        EducationalFeedback feedback = new EducationalFeedback(
            "mock-overuse",
            "warning",
            "High mock usage detected",
            evidence,
            "This test suite uses 47 @Mock annotations",
            "Replace value object mocks with real instances"
        );

        // Then
        assertEquals("mock-overuse", feedback.category);
        assertEquals("warning", feedback.severity);
        assertEquals("High mock usage detected", feedback.title);
        assertEquals(evidence, feedback.evidence);
        assertEquals("This test suite uses 47 @Mock annotations", feedback.message);
        assertEquals("Replace value object mocks with real instances", feedback.recommendation);
    }

    @Test
    void shouldSerializeToJson() {
        // Given
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("mockCount", 47);
        evidence.put("testCount", 23);

        EducationalFeedback feedback = new EducationalFeedback(
            "mock-overuse",
            "warning",
            "High mock usage",
            evidence,
            "Test message",
            "Test recommendation"
        );

        // When
        String json = gson.toJson(feedback);

        // Then
        assertTrue(json.contains("\"category\":\"mock-overuse\""));
        assertTrue(json.contains("\"severity\":\"warning\""));
        assertTrue(json.contains("\"title\":\"High mock usage\""));
        assertTrue(json.contains("\"mockCount\":47"));
        assertTrue(json.contains("\"testCount\":23"));
        assertTrue(json.contains("\"message\":\"Test message\""));
        assertTrue(json.contains("\"recommendation\":\"Test recommendation\""));
    }

    @Test
    void shouldDeserializeFromJson() {
        // Given
        String json = "{" +
            "\"category\":\"test-fixtures-opportunity\"," +
            "\"severity\":\"info\"," +
            "\"title\":\"Test-fixtures could simplify setup\"," +
            "\"evidence\":{\"compilationMs\":3000,\"depthAvg\":\"4.2\"}," +
            "\"message\":\"Test compilation took 3000ms\"," +
            "\"recommendation\":\"Consider implementing test-fixtures\"" +
            "}";

        // When
        EducationalFeedback feedback = gson.fromJson(json, EducationalFeedback.class);

        // Then
        assertEquals("test-fixtures-opportunity", feedback.category);
        assertEquals("info", feedback.severity);
        assertEquals("Test-fixtures could simplify setup", feedback.title);
        assertEquals(3000.0, feedback.evidence.get("compilationMs"));
        assertEquals("4.2", feedback.evidence.get("depthAvg"));
        assertEquals("Test compilation took 3000ms", feedback.message);
        assertEquals("Consider implementing test-fixtures", feedback.recommendation);
    }

    @Test
    void shouldHandleEmptyEvidence() {
        // Given
        Map<String, Object> emptyEvidence = new HashMap<>();

        // When
        EducationalFeedback feedback = new EducationalFeedback(
            "test-category",
            "info",
            "Test title",
            emptyEvidence,
            "Test message",
            "Test recommendation"
        );

        // Then
        assertNotNull(feedback.evidence);
        assertTrue(feedback.evidence.isEmpty());
    }

    @Test
    void shouldRejectNullCategory() {
        // Given
        Map<String, Object> evidence = new HashMap<>();

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            new EducationalFeedback(null, "warning", "Title", evidence, "Message", "Recommendation")
        );
    }

    @Test
    void shouldRejectEmptyCategory() {
        // Given
        Map<String, Object> evidence = new HashMap<>();

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            new EducationalFeedback("", "warning", "Title", evidence, "Message", "Recommendation")
        );
    }

    @Test
    void shouldRejectNullSeverity() {
        // Given
        Map<String, Object> evidence = new HashMap<>();

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            new EducationalFeedback("category", null, "Title", evidence, "Message", "Recommendation")
        );
    }

    @Test
    void shouldRejectInvalidSeverity() {
        // Given
        Map<String, Object> evidence = new HashMap<>();

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            new EducationalFeedback("category", "invalid", "Title", evidence, "Message", "Recommendation")
        );
    }

    @Test
    void shouldAcceptInfoSeverity() {
        // Given
        Map<String, Object> evidence = new HashMap<>();

        // When
        EducationalFeedback feedback = new EducationalFeedback(
            "category", "info", "Title", evidence, "Message", "Recommendation"
        );

        // Then
        assertEquals("info", feedback.severity);
    }

    @Test
    void shouldAcceptWarningSeverity() {
        // Given
        Map<String, Object> evidence = new HashMap<>();

        // When
        EducationalFeedback feedback = new EducationalFeedback(
            "category", "warning", "Title", evidence, "Message", "Recommendation"
        );

        // Then
        assertEquals("warning", feedback.severity);
    }

    @Test
    void shouldRejectNullEvidence() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            new EducationalFeedback("category", "warning", "Title", null, "Message", "Recommendation")
        );
    }
}
