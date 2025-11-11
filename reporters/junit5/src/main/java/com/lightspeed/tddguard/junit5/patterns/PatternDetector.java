package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Interface for detecting TDD anti-patterns in test code.
 * All detectors must provide fact-based feedback with measurable evidence.
 * NO predictions, speculation, or hypotheses allowed.
 */
public interface PatternDetector {

    /**
     * Detects patterns in test code and returns educational feedback if found.
     *
     * @param testResults  Test execution results
     * @param projectRoot  Project root directory
     * @param buildMetrics Build and compilation metrics
     * @return Educational feedback if pattern detected, empty otherwise
     */
    Optional<EducationalFeedback> detect(
        TestJson testResults,
        Path projectRoot,
        BuildMetrics buildMetrics
    );

    /**
     * Returns the pattern category this detector handles.
     *
     * @return Pattern category identifier
     */
    String getCategory();
}
