package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.SourceDirectoryResolver;
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
 * Tests for TestFixturesOpportunityDetector.
 * Validates fact-based detection of test-fixtures opportunities.
 */
class TestFixturesOpportunityDetectorTest {

    private TestFixturesOpportunityDetector createDetector(Path projectRoot) {
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(projectRoot, key -> null, key -> null);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);
        return new TestFixturesOpportunityDetector(analyzer);
    }

    @Test
    void shouldReturnCorrectCategory(@TempDir Path projectRoot) {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        assertEquals("test-fixtures-opportunity", detector.getCategory());
    }

    @Test
    void shouldDetectSlowCompilation(@TempDir Path projectRoot) throws IOException {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given: Compilation time > 2000ms
        createSimpleTestFile(projectRoot, "SlowTest.java");

        TestJson testResults = createTestResults(5, 5, 0, 0);
        BuildMetrics buildMetrics = new BuildMetrics(3000L, false);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals("test-fixtures-opportunity", feedback.get().category);
        assertEquals("info", feedback.get().severity);

        Map<String, Object> evidence = feedback.get().evidence;
        assertEquals(3000L, evidence.get("compilationMs"));
    }

    @Test
    void shouldDetectHighDependencyDepth(@TempDir Path projectRoot) throws IOException {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given: High constructor complexity (4 or more parameters = depth > 3)
        String testCode = "public class ComplexTest {\n" +
            "    @BeforeEach\n" +
            "    void setup() {\n" +
            "        service = new Service(repo, cache, logger, metrics);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "ComplexTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());

        Map<String, Object> evidence = feedback.get().evidence;
        String depthAvg = (String) evidence.get("depthAvg");
        assertTrue(Double.parseDouble(depthAvg) > 3.0);
    }

    @Test
    void shouldNotDetectBelowThresholds(@TempDir Path projectRoot) throws IOException {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given: Fast compilation and low complexity
        createSimpleTestFile(projectRoot, "SimpleTest.java");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = new BuildMetrics(1500L, false);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent());
    }

    @Test
    void shouldIncludeCompilationTimeInEvidence(@TempDir Path projectRoot) throws IOException {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given
        createSimpleTestFile(projectRoot, "Test.java");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = new BuildMetrics(2500L, false);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals(2500L, feedback.get().evidence.get("compilationMs"));
    }

    @Test
    void shouldIncludeDependencyDepthInEvidence(@TempDir Path projectRoot) throws IOException {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "public class Test {\n" +
            "    void test() {\n" +
            "        new Service(repo, cache, logger, metrics);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "Test.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = new BuildMetrics(2500L, false);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertNotNull(feedback.get().evidence.get("depthAvg"));
    }

    @Test
    void shouldProvideRecommendation(@TempDir Path projectRoot) throws IOException {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given
        createSimpleTestFile(projectRoot, "Test.java");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = new BuildMetrics(3000L, false);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertNotNull(feedback.get().recommendation);
        assertFalse(feedback.get().recommendation.isEmpty());
    }

    @Test
    void shouldHandleEmptyProject(@TempDir Path projectRoot) {
        TestFixturesOpportunityDetector detector = createDetector(projectRoot);
        // Given: No test files
        TestJson testResults = createTestResults(0, 0, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent());
    }

    // === Helper Methods ===

    private void createSimpleTestFile(Path projectRoot, String filename) throws IOException {
        String code = "public class " + filename.replace(".java", "") + " {\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        assertEquals(1, 1);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, filename, code);
    }

    private void createTestFile(Path projectRoot, String filename, String content) throws IOException {
        Path testFile = projectRoot.resolve("src/test/java").resolve(filename);
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, content);
    }

    private TestJson createTestResults(int total, int passed, int failed, int skipped) {
        TestJson testJson = new TestJson();
        testJson.summary = new TestSummary(total, passed, failed, skipped);
        testJson.tests = List.of();
        testJson.failures = List.of();
        return testJson;
    }
}
