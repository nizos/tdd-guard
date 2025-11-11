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
 * Tests for FileStructureAnalyzer.
 * Validates fact-based detection of file structure inconsistencies.
 */
class FileStructureAnalyzerTest {

    private FileStructureAnalyzer createDetector(Path projectRoot) {
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(projectRoot, key -> null, key -> null);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);
        return new FileStructureAnalyzer(analyzer);
    }

    @Test
    void shouldReturnCorrectCategory(@TempDir Path projectRoot) {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        assertEquals("file-structure", detector.getCategory());
    }

    @Test
    void shouldDetectTestsInProductionDirectory(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Test file in src/main/java (CRITICAL ERROR)
        createFileInMainDirectory(projectRoot, "UserServiceTest.java", "public class UserServiceTest {}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals("file-structure", feedback.get().category);
        assertEquals("warning", feedback.get().severity);
        assertEquals("Tests Found in Production Code Directory", feedback.get().title);

        Map<String, Object> evidence = feedback.get().evidence;
        @SuppressWarnings("unchecked")
        List<String> testsInMain = (List<String>) evidence.get("testsInMain");
        assertEquals(1, testsInMain.size());
        assertTrue(testsInMain.get(0).contains("src/main/java"));
        assertTrue(testsInMain.get(0).contains("UserServiceTest.java"));
    }

    @Test
    void shouldDetectPackageStructureMismatch(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Test in wrong package structure
        // Production: com.example.service.UserService
        // Test: com.example.UserServiceTest (should be com.example.service.UserServiceTest)
        createProductionFile(projectRoot, "com/example/service/UserService.java",
            "package com.example.service;\npublic class UserService {}");
        createTestFile(projectRoot, "com/example/UserServiceTest.java",
            "package com.example;\npublic class UserServiceTest {}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals("file-structure", feedback.get().category);

        Map<String, Object> evidence = feedback.get().evidence;
        @SuppressWarnings("unchecked")
        List<Map<String, String>> packageMismatches =
            (List<Map<String, String>>) evidence.get("packageMismatches");

        assertFalse(packageMismatches.isEmpty());
        Map<String, String> mismatch = packageMismatches.get(0);
        assertEquals("UserServiceTest", mismatch.get("testClass"));
        assertEquals("com.example", mismatch.get("testPackage"));
        assertEquals("com.example.service", mismatch.get("expectedPackage"));
    }

    @Test
    void shouldDetectMissingTestSuffix(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Test file without Test suffix or prefix
        createTestFile(projectRoot, "com/example/UserValidator.java",
            "package com.example;\nimport org.junit.jupiter.api.Test;\n" +
            "public class UserValidator {\n" +
            "    @Test\n" +
            "    void shouldValidate() {}\n" +
            "}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());

        Map<String, Object> evidence = feedback.get().evidence;
        @SuppressWarnings("unchecked")
        List<String> namingViolations = (List<String>) evidence.get("namingViolations");

        assertFalse(namingViolations.isEmpty());
        assertTrue(namingViolations.stream()
            .anyMatch(v -> v.contains("UserValidator") && v.contains("Test suffix or Test prefix")));
    }

    @Test
    void shouldAcceptTestPrefix(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Test file with Test prefix (valid)
        createTestFile(projectRoot, "com/example/TestUserService.java",
            "package com.example;\nimport org.junit.jupiter.api.Test;\n" +
            "public class TestUserService {\n" +
            "    @Test\n" +
            "    void shouldProcess() {}\n" +
            "}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then - Should not detect naming violation for TestUserService
        if (feedback.isPresent()) {
            Map<String, Object> evidence = feedback.get().evidence;
            @SuppressWarnings("unchecked")
            List<String> namingViolations = (List<String>) evidence.get("namingViolations");
            if (namingViolations != null) {
                assertFalse(namingViolations.stream()
                    .anyMatch(v -> v.contains("TestUserService")));
            }
        }
    }

    @Test
    void shouldAcceptTestSuffix(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Test file with Test suffix (valid)
        createTestFile(projectRoot, "com/example/UserServiceTest.java",
            "package com.example;\nimport org.junit.jupiter.api.Test;\n" +
            "public class UserServiceTest {\n" +
            "    @Test\n" +
            "    void shouldProcess() {}\n" +
            "}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then - Should not detect naming violation for UserServiceTest
        if (feedback.isPresent()) {
            Map<String, Object> evidence = feedback.get().evidence;
            @SuppressWarnings("unchecked")
            List<String> namingViolations = (List<String>) evidence.get("namingViolations");
            if (namingViolations != null) {
                assertFalse(namingViolations.stream()
                    .anyMatch(v -> v.contains("UserServiceTest")));
            }
        }
    }

    @Test
    void shouldDetectMultipleIssuesTogether(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Multiple file structure issues
        createFileInMainDirectory(projectRoot, "InvalidTest.java", "public class InvalidTest {}");
        createTestFile(projectRoot, "com/example/UserValidator.java",
            "package com.example;\nimport org.junit.jupiter.api.Test;\n" +
            "public class UserValidator {\n" +
            "    @Test\n" +
            "    void test() {}\n" +
            "}");

        TestJson testResults = createTestResults(2, 2, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());

        Map<String, Object> evidence = feedback.get().evidence;

        @SuppressWarnings("unchecked")
        List<String> testsInMain = (List<String>) evidence.get("testsInMain");
        assertEquals(1, testsInMain.size());

        @SuppressWarnings("unchecked")
        List<String> namingViolations = (List<String>) evidence.get("namingViolations");
        assertEquals(1, namingViolations.size());
    }

    @Test
    void shouldNotDetectIssuesInCleanStructure(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Properly structured test files
        createTestFile(projectRoot, "com/example/service/UserServiceTest.java",
            "package com.example.service;\nimport org.junit.jupiter.api.Test;\n" +
            "public class UserServiceTest {\n" +
            "    @Test\n" +
            "    void shouldProcess() {}\n" +
            "}");

        createTestFile(projectRoot, "com/example/repository/TestUserRepository.java",
            "package com.example.repository;\nimport org.junit.jupiter.api.Test;\n" +
            "public class TestUserRepository {\n" +
            "    @Test\n" +
            "    void shouldSave() {}\n" +
            "}");

        TestJson testResults = createTestResults(2, 2, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then - No issues should be detected
        assertFalse(feedback.isPresent());
    }

    @Test
    void shouldProvideDetailedMessage(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given
        createFileInMainDirectory(projectRoot, "BadTest.java", "public class BadTest {}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String message = feedback.get().message;
        assertNotNull(message);
        assertFalse(message.isEmpty());
        assertTrue(message.contains("src/main/java"));
    }

    @Test
    void shouldProvideActionableRecommendation(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given
        createFileInMainDirectory(projectRoot, "BadTest.java", "public class BadTest {}");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String recommendation = feedback.get().recommendation;
        assertNotNull(recommendation);
        assertFalse(recommendation.isEmpty());
    }

    @Test
    void shouldHandleEmptyProjectGracefully(@TempDir Path projectRoot) {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Empty project with no test files
        TestJson testResults = createTestResults(0, 0, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then - Should not crash
        assertFalse(feedback.isPresent());
    }

    @Test
    void shouldIgnoreBuildDirectories(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Test file in build directory (should be ignored)
        Path buildDir = projectRoot.resolve("build/classes/java/test/com/example");
        Files.createDirectories(buildDir);
        Files.writeString(buildDir.resolve("CompiledTest.java"), "public class CompiledTest {}");

        TestJson testResults = createTestResults(0, 0, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then - Should not detect issues in build directory
        assertFalse(feedback.isPresent());
    }

    @Test
    void shouldHandleKotlinFiles(@TempDir Path projectRoot) throws IOException {
        FileStructureAnalyzer detector = createDetector(projectRoot);
        // Given: Kotlin test file in wrong location
        createFileInMainDirectoryKotlin(projectRoot, "UserServiceTest.kt", "class UserServiceTest");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());

        Map<String, Object> evidence = feedback.get().evidence;
        @SuppressWarnings("unchecked")
        List<String> testsInMain = (List<String>) evidence.get("testsInMain");
        assertFalse(testsInMain.isEmpty());
        assertTrue(testsInMain.get(0).contains("UserServiceTest.kt"));
    }

    // === Helper Methods ===

    private void createFileInMainDirectory(Path projectRoot, String filename, String content) throws IOException {
        Path file = projectRoot.resolve("src/main/java").resolve(filename);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void createFileInMainDirectoryKotlin(Path projectRoot, String filename, String content) throws IOException {
        Path file = projectRoot.resolve("src/main/kotlin").resolve(filename);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void createTestFile(Path projectRoot, String relativePath, String content) throws IOException {
        Path file = projectRoot.resolve("src/test/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private void createProductionFile(Path projectRoot, String relativePath, String content) throws IOException {
        Path file = projectRoot.resolve("src/main/java").resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private TestJson createTestResults(int total, int passed, int failed, int skipped) {
        TestJson testJson = new TestJson();
        testJson.summary = new TestSummary(total, passed, failed, skipped);
        testJson.tests = List.of();
        testJson.failures = List.of();
        return testJson;
    }
}
