package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.model.TestSummary;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for GradleBuildOptimizationDetector.
 * Validates fact-based detection of Gradle build optimization issues.
 */
class GradleBuildOptimizationDetectorTest {

    private final GradleBuildOptimizationDetector detector = new GradleBuildOptimizationDetector();

    @Test
    void shouldReturnCorrectCategory() {
        assertEquals("gradle-incremental-compilation", detector.getCategory());
    }

    @Test
    void shouldDetectHighBuildTimeVariance(@TempDir Path projectRoot) {
        // Given: Build times with 35% variance (high > 20% threshold)
        // Build times: 100ms, 135ms, 100ms (avg 111.67ms, max deviation 23.33ms, variance 20.9%)
        // Let's use more extreme: 100ms, 150ms, 100ms (avg 116.67ms, max deviation 33.33ms, variance 28.6%)
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Should detect high variance");
        assertEquals("gradle-incremental-compilation", feedback.get().category);
        assertEquals("info", feedback.get().severity);
        assertEquals("Gradle incremental compilation issue detected", feedback.get().title);

        Map<String, Object> evidence = feedback.get().evidence;
        assertNotNull(evidence.get("variancePercent"));

        // Variance should be approximately 28.6%
        String varianceStr = (String) evidence.get("variancePercent");
        double variance = Double.parseDouble(varianceStr);
        assertTrue(variance > 20.0, "Variance should be > 20%");
    }

    @Test
    void shouldNotDetectLowVariance(@TempDir Path projectRoot) {
        // Given: Build times with 8% variance (low < 20% threshold)
        // Build times: 1000ms, 1080ms, 1000ms (avg 1026.67ms, max deviation 53.33ms, variance 5.2%)
        List<Long> buildTimes = List.of(1000L, 1050L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent(), "Should not detect low variance");
    }

    @Test
    void shouldNotDetectWithInsufficientHistory(@TempDir Path projectRoot) {
        // Given: Only 2 builds (need at least 3)
        List<Long> buildTimes = List.of(1000L, 1500L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent(), "Should not detect with insufficient history");
    }

    @Test
    void shouldDetectIncrementalCompilationDisabled(@TempDir Path projectRoot) throws IOException {
        // Given: No gradle.properties file (incremental disabled by default)
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        Map<String, Object> evidence = feedback.get().evidence;
        assertEquals(false, evidence.get("incrementalEnabled"));
    }

    @Test
    void shouldDetectIncrementalCompilationEnabled(@TempDir Path projectRoot) throws IOException {
        // Given: gradle.properties with caching enabled
        createGradleProperties(projectRoot, "org.gradle.caching=true\n");

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        Map<String, Object> evidence = feedback.get().evidence;
        assertEquals(true, evidence.get("incrementalEnabled"));
    }

    @Test
    void shouldIgnoreCachingDisabled(@TempDir Path projectRoot) throws IOException {
        // Given: gradle.properties with caching explicitly disabled
        createGradleProperties(projectRoot, "org.gradle.caching=false\n");

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        Map<String, Object> evidence = feedback.get().evidence;
        assertEquals(false, evidence.get("incrementalEnabled"));
    }

    @Test
    void shouldDetectLombokProcessor(@TempDir Path projectRoot) throws IOException {
        // Given: build.gradle with Lombok dependency
        createBuildGradle(projectRoot,
            "dependencies {\n" +
            "    compileOnly 'org.projectlombok:lombok:1.18.28'\n" +
            "}\n"
        );

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        @SuppressWarnings("unchecked")
        List<String> processors = (List<String>) feedback.get().evidence.get("annotationProcessors");
        assertTrue(processors.contains("Lombok"));
    }

    @Test
    void shouldDetectMultipleAnnotationProcessors(@TempDir Path projectRoot) throws IOException {
        // Given: build.gradle with multiple annotation processors
        createBuildGradle(projectRoot,
            "dependencies {\n" +
            "    compileOnly 'org.projectlombok:lombok:1.18.28'\n" +
            "    implementation 'org.mapstruct:mapstruct:1.5.5.Final'\n" +
            "    implementation 'com.querydsl:querydsl-jpa:5.0.0'\n" +
            "}\n"
        );

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        @SuppressWarnings("unchecked")
        List<String> processors = (List<String>) feedback.get().evidence.get("annotationProcessors");
        assertTrue(processors.contains("Lombok"));
        assertTrue(processors.contains("MapStruct"));
        assertTrue(processors.contains("QueryDSL"));
    }

    @Test
    void shouldSupportBuildGradleKts(@TempDir Path projectRoot) throws IOException {
        // Given: build.gradle.kts (Kotlin DSL) with Lombok
        createBuildGradleKts(projectRoot,
            "dependencies {\n" +
            "    compileOnly(\"org.projectlombok:lombok:1.18.28\")\n" +
            "}\n"
        );

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        @SuppressWarnings("unchecked")
        List<String> processors = (List<String>) feedback.get().evidence.get("annotationProcessors");
        assertTrue(processors.contains("Lombok"));
    }

    @Test
    void shouldRecommendEnablingIncrementalWhenDisabled(@TempDir Path projectRoot) throws IOException {
        // Given: Incremental compilation disabled
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String recommendation = feedback.get().recommendation;
        assertTrue(recommendation.contains("org.gradle.caching=true"),
            "Should recommend enabling caching");
        assertTrue(recommendation.contains("org.gradle.parallel=true"),
            "Should recommend enabling parallel builds");
    }

    @Test
    void shouldRecommendAnnotationProcessorConfigWhenProcessorsDetected(@TempDir Path projectRoot) throws IOException {
        // Given: Annotation processors present
        createBuildGradle(projectRoot,
            "dependencies {\n" +
            "    compileOnly 'org.projectlombok:lombok:1.18.28'\n" +
            "}\n"
        );
        createGradleProperties(projectRoot, "org.gradle.caching=true\n");

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String recommendation = feedback.get().recommendation;
        assertTrue(recommendation.contains("annotation processor"),
            "Should mention annotation processors");
    }

    @Test
    void shouldIncludeBuildTimesInEvidence(@TempDir Path projectRoot) {
        // Given
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        Map<String, Object> evidence = feedback.get().evidence;
        @SuppressWarnings("unchecked")
        List<Long> evidenceTimes = (List<Long>) evidence.get("buildTimes");
        assertEquals(buildTimes, evidenceTimes);
    }

    @Test
    void shouldIncludeVarianceInMessage(@TempDir Path projectRoot) {
        // Given
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String message = feedback.get().message;
        assertTrue(message.contains("variance"), "Message should mention variance");
        assertTrue(message.contains("%"), "Message should include percentage");
    }

    // === Helper Methods ===

    private void createGradleProperties(Path projectRoot, String content) throws IOException {
        Path gradleProps = projectRoot.resolve("gradle.properties");
        Files.writeString(gradleProps, content);
    }

    private void createBuildGradle(Path projectRoot, String content) throws IOException {
        Path buildGradle = projectRoot.resolve("build.gradle");
        Files.writeString(buildGradle, content);
    }

    private void createBuildGradleKts(Path projectRoot, String content) throws IOException {
        Path buildGradle = projectRoot.resolve("build.gradle.kts");
        Files.writeString(buildGradle, content);
    }

    private TestJson createTestResults(int total, int passed, int failed, int skipped) {
        TestJson testJson = new TestJson();
        testJson.summary = new TestSummary(total, passed, failed, skipped);
        testJson.tests = List.of();
        testJson.failures = List.of();
        return testJson;
    }
}
