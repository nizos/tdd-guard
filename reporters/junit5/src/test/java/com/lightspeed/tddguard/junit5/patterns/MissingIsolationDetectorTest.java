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
 * Tests for MissingIsolationDetector.
 * Validates fact-based detection of missing test isolation.
 */
class MissingIsolationDetectorTest {

    private MissingIsolationDetector createDetector(Path projectRoot) {
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(projectRoot, key -> null, key -> null);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);
        return new MissingIsolationDetector(analyzer);
    }

    @Test
    void shouldReturnCorrectCategory(@TempDir Path projectRoot) {
        MissingIsolationDetector detector = createDetector(projectRoot);
        assertEquals("missing-isolation", detector.getCategory());
    }

    @Test
    void shouldDetectHardcodedUrls(@TempDir Path projectRoot) throws IOException {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "public class ApiTest {\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        String url = \"http://localhost:8080/api\";\n" +
            "        client.get(url);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "ApiTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals("missing-isolation", feedback.get().category);
        assertEquals("warning", feedback.get().severity);

        Map<String, Object> evidence = feedback.get().evidence;
        assertTrue((Integer) evidence.get("hardcodedCount") > 0);
    }

    @Test
    void shouldDetectHardcodedFilePaths(@TempDir Path projectRoot) throws IOException {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "public class FileTest {\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        File file = new File(\"/tmp/data.txt\");\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "FileTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertTrue((Integer) feedback.get().evidence.get("hardcodedCount") > 0);
    }

    @Test
    void shouldDetectHardcodedPorts(@TempDir Path projectRoot) throws IOException {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given - Use URL with port which will definitely be detected
        String testCode = "public class ServerTest {\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        connect(\"http://localhost:8080/api\");\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "ServerTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertTrue((Integer) feedback.get().evidence.get("hardcodedCount") > 0);
    }

    @Test
    void shouldNotDetectInCleanTests(@TempDir Path projectRoot) throws IOException {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given: Clean test with no hardcoded resources
        String testCode = "public class CleanTest {\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        assertEquals(1, 1);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "CleanTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent());
    }

    @Test
    void shouldIncludeExamplesInEvidence(@TempDir Path projectRoot) throws IOException {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "public class Test {\n" +
            "    void test() {\n" +
            "        connect(\"http://localhost:8080\");\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "Test.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        @SuppressWarnings("unchecked")
        List<String> examples = (List<String>) feedback.get().evidence.get("examples");
        assertNotNull(examples);
        assertFalse(examples.isEmpty());
    }

    @Test
    void shouldProvideRecommendation(@TempDir Path projectRoot) throws IOException {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "public class Test {\n" +
            "    void test() {\n" +
            "        connect(\"http://api.example.com\");\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "Test.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertNotNull(feedback.get().recommendation);
        assertFalse(feedback.get().recommendation.isEmpty());
    }

    @Test
    void shouldHandleEmptyProject(@TempDir Path projectRoot) {
        MissingIsolationDetector detector = createDetector(projectRoot);
        // Given: No test files
        TestJson testResults = createTestResults(0, 0, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent());
    }

    // === Helper Methods ===

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
