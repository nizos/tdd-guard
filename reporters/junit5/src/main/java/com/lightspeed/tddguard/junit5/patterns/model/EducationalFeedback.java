package com.lightspeed.tddguard.junit5.patterns.model;

import java.util.Map;
import java.util.Set;

/**
 * Educational feedback about detected TDD anti-patterns.
 * All feedback is fact-based with measurable evidence.
 * NO predictions, speculation, or hypotheses allowed.
 */
public class EducationalFeedback {

    private static final Set<String> VALID_SEVERITIES = Set.of("info", "warning");

    public final String category;
    public final String severity;
    public final String title;
    public final Map<String, Object> evidence;
    public final String message;
    public final String recommendation;

    /**
     * Creates educational feedback with validation.
     *
     * @param category       Pattern category (e.g., "mock-overuse")
     * @param severity       Severity level: "info" or "warning"
     * @param title          Short descriptive title
     * @param evidence       Fact-based measurements (mockCount, ratio, etc.)
     * @param message        Detailed explanation with specific measurements
     * @param recommendation Actionable suggestion based on detected pattern
     * @throws IllegalArgumentException if validation fails
     */
    public EducationalFeedback(
        String category,
        String severity,
        String title,
        Map<String, Object> evidence,
        String message,
        String recommendation
    ) {
        validateCategory(category);
        validateSeverity(severity);
        validateEvidence(evidence);

        this.category = category;
        this.severity = severity;
        this.title = title;
        this.evidence = evidence;
        this.message = message;
        this.recommendation = recommendation;
    }

    private void validateCategory(String category) {
        if (category == null || category.isEmpty()) {
            throw new IllegalArgumentException("Category cannot be null or empty");
        }
    }

    private void validateSeverity(String severity) {
        if (severity == null) {
            throw new IllegalArgumentException("Severity cannot be null");
        }
        if (!VALID_SEVERITIES.contains(severity)) {
            throw new IllegalArgumentException(
                "Invalid severity: " + severity + ". Must be one of: " + VALID_SEVERITIES
            );
        }
    }

    private void validateEvidence(Map<String, Object> evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Evidence cannot be null");
        }
    }
}
