package com.lightspeed.tddguard.junit5.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.model.TestSummary;
import com.lightspeed.tddguard.junit5.patterns.BuildMetrics;
import com.lightspeed.tddguard.junit5.patterns.GradleBuildOptimizationDetector;
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
 * Manual E2E integration test for Story #4: Gradle Build Optimization Detection.
 * Tests actual detection of Gradle configuration issues with real files.
 */
class GradleBuildOptimizationIntegrationTest {

    private final GradleBuildOptimizationDetector detector = new GradleBuildOptimizationDetector();
    private final Gson gson = new Gson();

    @Test
    void scenario1_highVarianceDetection(@TempDir Path projectRoot) throws IOException {
        // Given: High build variance (42.9% variance)
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);
        TestJson testResults = createTestResults(10, 10, 0, 0);

        // When: Running detector
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then: Should detect high variance
        assertTrue(feedback.isPresent(), "Should detect high variance");
        EducationalFeedback result = feedback.get();

        // Verify structure
        assertEquals("gradle-incremental-compilation", result.category);
        assertEquals("info", result.severity);
        assertEquals("Gradle incremental compilation issue detected", result.title);

        // Verify evidence
        Map<String, Object> evidence = result.evidence;
        String varianceStr = (String) evidence.get("variancePercent");
        double variance = Double.parseDouble(varianceStr);
        assertTrue(variance > 20.0, "Variance should be > 20%, was: " + variance);
        assertEquals(false, evidence.get("incrementalEnabled"));
        @SuppressWarnings("unchecked")
        List<String> processors = (List<String>) evidence.get("annotationProcessors");
        assertTrue(processors.isEmpty(), "No processors should be detected");
        @SuppressWarnings("unchecked")
        List<Long> times = (List<Long>) evidence.get("buildTimes");
        assertEquals(buildTimes, times);

        // Verify message
        assertTrue(result.message.contains("Build time variance"));
        assertTrue(result.message.contains("Incremental compilation enabled: false"));
        assertTrue(result.message.contains("High variance suggests incremental compilation not working"));

        // Verify recommendation
        assertTrue(result.recommendation.contains("org.gradle.caching=true"));
        assertTrue(result.recommendation.contains("org.gradle.parallel=true"));

        System.out.println("✅ Scenario 1 PASSED: High variance detection");
    }

    @Test
    void scenario2_incrementalCompilationDisabled(@TempDir Path projectRoot) throws IOException {
        // Given: High variance + no gradle.properties (incremental disabled)
        List<Long> buildTimes = List.of(1000L, 1400L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);
        TestJson testResults = createTestResults(5, 5, 0, 0);

        // When: Running detector
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then: Should recommend enabling incremental compilation
        assertTrue(feedback.isPresent(), "Should detect disabled incremental compilation");
        EducationalFeedback result = feedback.get();

        // Verify incremental disabled
        Map<String, Object> evidence = result.evidence;
        assertEquals(false, evidence.get("incrementalEnabled"));

        // Verify recommendation contains gradle.properties settings
        assertTrue(result.recommendation.contains("gradle.properties"));
        assertTrue(result.recommendation.contains("org.gradle.caching=true"));
        assertTrue(result.recommendation.contains("org.gradle.parallel=true"));

        System.out.println("✅ Scenario 2 PASSED: Incremental compilation disabled detection");
    }

    @Test
    void scenario3_annotationProcessorsDetected(@TempDir Path projectRoot) throws IOException {
        // Given: High variance + Lombok in build.gradle
        createBuildGradle(projectRoot,
            "dependencies {\n" +
            "    compileOnly 'org.projectlombok:lombok:1.18.28'\n" +
            "    annotationProcessor 'org.projectlombok:lombok:1.18.28'\n" +
            "}\n"
        );

        List<Long> buildTimes = List.of(2000L, 3000L, 2000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);
        TestJson testResults = createTestResults(8, 8, 0, 0);

        // When: Running detector
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then: Should detect annotation processors
        assertTrue(feedback.isPresent(), "Should detect annotation processors");
        EducationalFeedback result = feedback.get();

        // Verify processors detected
        Map<String, Object> evidence = result.evidence;
        @SuppressWarnings("unchecked")
        List<String> processors = (List<String>) evidence.get("annotationProcessors");
        assertTrue(processors.contains("Lombok"), "Should detect Lombok");

        // Verify message mentions processors
        assertTrue(result.message.contains("Annotation processors detected: Lombok"));

        // Verify recommendation mentions processor configuration
        assertTrue(result.recommendation.contains("Annotation processors detected"));
        assertTrue(result.recommendation.contains("annotation processor"));

        System.out.println("✅ Scenario 3 PASSED: Annotation processor detection");
    }

    @Test
    void scenario4_multipleIssuesCombined(@TempDir Path projectRoot) throws IOException {
        // Given: High variance + incremental disabled + multiple annotation processors
        createBuildGradle(projectRoot,
            "dependencies {\n" +
            "    compileOnly 'org.projectlombok:lombok:1.18.28'\n" +
            "    implementation 'org.mapstruct:mapstruct:1.5.5.Final'\n" +
            "    implementation 'com.querydsl:querydsl-jpa:5.0.0'\n" +
            "}\n"
        );
        // No gradle.properties = incremental disabled

        List<Long> buildTimes = List.of(1500L, 2500L, 1500L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);
        TestJson testResults = createTestResults(12, 12, 0, 0);

        // When: Running detector
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then: Should detect all issues
        assertTrue(feedback.isPresent(), "Should detect multiple issues");
        EducationalFeedback result = feedback.get();

        // Verify evidence
        Map<String, Object> evidence = result.evidence;
        assertEquals(false, evidence.get("incrementalEnabled"));
        @SuppressWarnings("unchecked")
        List<String> processors = (List<String>) evidence.get("annotationProcessors");
        assertEquals(3, processors.size(), "Should detect 3 processors");
        assertTrue(processors.contains("Lombok"));
        assertTrue(processors.contains("MapStruct"));
        assertTrue(processors.contains("QueryDSL"));

        // Verify message contains all info
        assertTrue(result.message.contains("Incremental compilation enabled: false"));
        assertTrue(result.message.contains("Lombok"));
        assertTrue(result.message.contains("MapStruct"));
        assertTrue(result.message.contains("QueryDSL"));

        // Verify recommendation addresses both issues
        assertTrue(result.recommendation.contains("gradle.properties"));
        assertTrue(result.recommendation.contains("Annotation processors detected"));

        System.out.println("✅ Scenario 4 PASSED: Multiple issues combined detection");
    }

    @Test
    void scenario5_forbiddenWordsValidation(@TempDir Path projectRoot) throws IOException {
        // Given: Any detection scenario
        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);
        TestJson testResults = createTestResults(5, 5, 0, 0);

        // When: Running detector
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then: Messages should not contain forbidden words
        assertTrue(feedback.isPresent(), "Should detect issue");
        EducationalFeedback result = feedback.get();

        String[] forbiddenWords = {
            "should", "could", "would", "might", "may", "possibly", "perhaps",
            "probably", "likely", "seems", "appears"
        };

        String combinedText = result.message.toLowerCase() + " " + result.recommendation.toLowerCase();

        for (String forbidden : forbiddenWords) {
            assertFalse(combinedText.contains(" " + forbidden + " "),
                "Message/recommendation should not contain forbidden word: " + forbidden);
        }

        System.out.println("✅ Scenario 5 PASSED: No forbidden words in messages");
    }

    @Test
    void acceptanceCriterion1_buildTimeVarianceMeasurement(@TempDir Path projectRoot) {
        // AC1: Build Time Variance Measurement
        // Verify variance calculation and threshold detection

        // Test 1: High variance (> 20%) triggers detection
        List<Long> highVariance = List.of(1000L, 1500L, 1000L);
        BuildMetrics highMetrics = BuildMetrics.withHistory(highVariance);
        TestJson testResults = createTestResults(5, 5, 0, 0);

        Optional<EducationalFeedback> highFeedback = detector.detect(testResults, projectRoot, highMetrics);
        assertTrue(highFeedback.isPresent(), "High variance should trigger detection");

        // Test 2: Low variance (< 20%) does NOT trigger
        List<Long> lowVariance = List.of(1000L, 1050L, 1000L);
        BuildMetrics lowMetrics = BuildMetrics.withHistory(lowVariance);

        Optional<EducationalFeedback> lowFeedback = detector.detect(testResults, projectRoot, lowMetrics);
        assertFalse(lowFeedback.isPresent(), "Low variance should NOT trigger detection");

        System.out.println("✅ AC1 PASSED: Build time variance measurement and threshold detection verified");
    }

    @Test
    void acceptanceCriterion2_gradleConfigurationTemplates(@TempDir Path projectRoot) throws IOException {
        // AC2: Gradle Configuration Templates
        // Verify specific gradle.properties recommendations

        List<Long> buildTimes = List.of(1000L, 1500L, 1000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);
        TestJson testResults = createTestResults(5, 5, 0, 0);

        // Test: Recommendation contains exact gradle.properties configuration
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);
        assertTrue(feedback.isPresent(), "Should provide feedback");

        String recommendation = feedback.get().recommendation;

        // Verify exact configuration lines
        assertTrue(recommendation.contains("org.gradle.caching=true"),
            "Should contain exact caching config");
        assertTrue(recommendation.contains("org.gradle.parallel=true"),
            "Should contain exact parallel config");

        // Verify recommendation format
        assertTrue(recommendation.contains("gradle.properties"),
            "Should reference gradle.properties file");

        System.out.println("✅ AC2 PASSED: Gradle configuration templates verified");
    }

    // === Helper Methods ===

    private void createBuildGradle(Path projectRoot, String content) throws IOException {
        Path buildGradle = projectRoot.resolve("build.gradle");
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
