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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates that all educational feedback messages are fact-based
 * and do not contain forbidden speculative words.
 */
class ForbiddenWordsValidationTest {

    private static final List<String> FORBIDDEN_WORDS = Arrays.asList(
        "could", "can", "should", "will", "might", "may", "would"
    );

    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
        "\\b(could|can|should|will|might|may|would)\\b",
        Pattern.CASE_INSENSITIVE
    );

    // Helper methods to create detectors
    private SourceAnalyzer createSourceAnalyzer(Path projectRoot) {
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(projectRoot, key -> null, key -> null);
        return new SourceAnalyzer(resolver);
    }

    @Test
    void mockOveruseDetectorMessagesMustNotContainForbiddenWords(@TempDir Path projectRoot) throws IOException {
        // Given: Create scenario that triggers mock overuse detection
        MockOveruseDetector detector = new MockOveruseDetector(createSourceAnalyzer(projectRoot));

        String testCode = "import org.mockito.Mock;\n\n" +
            "public class OrderTest {\n" +
            "    @Mock private UserId userId;\n" +
            "    @Mock private OrderId orderId;\n" +
            "    @Mock private Money amount;\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/test/java/OrderTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Detector should trigger");
        assertNoForbiddenWords(feedback.get().message, "MockOveruseDetector message");
        assertNoForbiddenWords(feedback.get().recommendation, "MockOveruseDetector recommendation");
        assertNoForbiddenWords(feedback.get().title, "MockOveruseDetector title");
    }

    @Test
    void testFixturesDetectorMessagesMustNotContainForbiddenWords(@TempDir Path projectRoot) throws IOException {
        // Given: Create scenario that triggers test-fixtures opportunity detection
        TestFixturesOpportunityDetector detector = new TestFixturesOpportunityDetector(createSourceAnalyzer(projectRoot));

        String testCode = "public class ComplexTest {\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        Service s = new Service(new Repo(), new Cache(), new Logger(), new Config());\n" +
            "    }\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/test/java/ComplexTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = new BuildMetrics(5000L, false); // High compilation time

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Detector should trigger");
        assertNoForbiddenWords(feedback.get().message, "TestFixturesOpportunityDetector message");
        assertNoForbiddenWords(feedback.get().recommendation, "TestFixturesOpportunityDetector recommendation");
        assertNoForbiddenWords(feedback.get().title, "TestFixturesOpportunityDetector title");
    }

    @Test
    void missingIsolationDetectorMessagesMustNotContainForbiddenWords(@TempDir Path projectRoot) throws IOException {
        // Given: Create scenario that triggers missing isolation detection
        MissingIsolationDetector detector = new MissingIsolationDetector(createSourceAnalyzer(projectRoot));

        String testCode = "public class ApiTest {\n" +
            "    private static final String API_URL = \"https://api.example.com:8080/v1\";\n\n" +
            "    @Test\n" +
            "    void testApi() {\n" +
            "        HttpClient client = new HttpClient(API_URL);\n" +
            "    }\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/test/java/ApiTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Detector should trigger");
        assertNoForbiddenWords(feedback.get().message, "MissingIsolationDetector message");
        assertNoForbiddenWords(feedback.get().recommendation, "MissingIsolationDetector recommendation");
        assertNoForbiddenWords(feedback.get().title, "MissingIsolationDetector title");
    }

    @Test
    void gradleBuildOptimizationDetectorMessagesMustNotContainForbiddenWords(@TempDir Path projectRoot) throws IOException {
        // Given: Create scenario that triggers Gradle build optimization detection
        GradleBuildOptimizationDetector detector = new GradleBuildOptimizationDetector();

        // Create build.gradle with annotation processors
        String buildGradle = "dependencies {\n" +
            "    annotationProcessor 'org.projectlombok:lombok:1.18.30'\n" +
            "}\n";

        Path buildFile = projectRoot.resolve("build.gradle");
        Files.writeString(buildFile, buildGradle);

        TestJson testResults = createTestResults(1, 1, 0, 0);

        // Create build metrics with high variance (3 builds with significant variance)
        List<Long> buildTimes = Arrays.asList(1000L, 3000L, 2000L);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(buildTimes);

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Detector should trigger with high variance");
        assertNoForbiddenWords(feedback.get().message, "GradleBuildOptimizationDetector message");
        assertNoForbiddenWords(feedback.get().recommendation, "GradleBuildOptimizationDetector recommendation");
        assertNoForbiddenWords(feedback.get().title, "GradleBuildOptimizationDetector title");
    }

    @Test
    void fileStructureAnalyzerMessagesMustNotContainForbiddenWords(@TempDir Path projectRoot) throws IOException {
        // Given: Create scenario that triggers file structure detection
        FileStructureAnalyzer detector = new FileStructureAnalyzer(createSourceAnalyzer(projectRoot));

        // Create test file in production directory
        String testCode = "import org.junit.jupiter.api.Test;\n\n" +
            "public class BadTest {\n" +
            "    @Test\n" +
            "    void test() {}\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/main/java/BadTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Detector should trigger");
        assertNoForbiddenWords(feedback.get().message, "FileStructureAnalyzer message");
        assertNoForbiddenWords(feedback.get().recommendation, "FileStructureAnalyzer recommendation");
        assertNoForbiddenWords(feedback.get().title, "FileStructureAnalyzer title");
    }

    @Test
    void fileStructureAnalyzerPackageMismatchMessagesMustNotContainForbiddenWords(@TempDir Path projectRoot) throws IOException {
        // Given: Create scenario that triggers package mismatch detection
        FileStructureAnalyzer detector = new FileStructureAnalyzer(createSourceAnalyzer(projectRoot));

        // Create production class in com.example.services package
        String productionCode = "package com.example.services;\n\n" +
            "public class User {\n" +
            "    private String name;\n" +
            "}\n";

        Path productionFile = projectRoot.resolve("src/main/java/com/example/services/User.java");
        Files.createDirectories(productionFile.getParent());
        Files.writeString(productionFile, productionCode);

        // Create test class in different package (com.example instead of com.example.services)
        String testCode = "package com.example;\n\n" +
            "import org.junit.jupiter.api.Test;\n\n" +
            "public class UserTest {\n" +
            "    @Test\n" +
            "    void test() {}\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/test/java/com/example/UserTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent(), "Detector should trigger on package mismatch");
        assertNoForbiddenWords(feedback.get().message, "FileStructureAnalyzer package mismatch message");
        assertNoForbiddenWords(feedback.get().recommendation, "FileStructureAnalyzer package mismatch recommendation");
        assertNoForbiddenWords(feedback.get().title, "FileStructureAnalyzer package mismatch title");
    }

    @Test
    void allDetectorsShouldProvideForbiddenWordFreeMessages(@TempDir Path projectRoot) throws IOException {
        // Given: Create test scenario that might trigger multiple detectors
        String testCode = "import org.mockito.Mock;\n\n" +
            "public class ServiceTest {\n" +
            "    private static Service sharedService;\n\n" +
            "    @Mock private Repository repo1;\n" +
            "    @Mock private Repository repo2;\n" +
            "    @Mock private Repository repo3;\n\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        Service s = new Service(new A(), new B(), new C(), new D());\n" +
            "    }\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/test/java/ServiceTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.withHistory(Arrays.asList(1000L, 3000L, 2000L));

        List<PatternDetector> detectors = Arrays.asList(
            new MockOveruseDetector(createSourceAnalyzer(projectRoot)),
            new TestFixturesOpportunityDetector(createSourceAnalyzer(projectRoot)),
            new MissingIsolationDetector(createSourceAnalyzer(projectRoot)),
            new GradleBuildOptimizationDetector(),
            new FileStructureAnalyzer(createSourceAnalyzer(projectRoot))
        );

        // When/Then: Check all detectors
        for (PatternDetector detector : detectors) {
            Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

            if (feedback.isPresent()) {
                String detectorName = detector.getCategory();
                assertNoForbiddenWords(feedback.get().message, detectorName + " message");
                assertNoForbiddenWords(feedback.get().recommendation, detectorName + " recommendation");
                assertNoForbiddenWords(feedback.get().title, detectorName + " title");
            }
        }
    }

    // === Helper Methods ===

    private void assertNoForbiddenWords(String text, String fieldDescription) {
        assertNotNull(text, fieldDescription + " must not be null");

        java.util.regex.Matcher matcher = FORBIDDEN_PATTERN.matcher(text);

        if (matcher.find()) {
            String foundWord = matcher.group();
            fail(String.format(
                "%s contains forbidden word '%s'. " +
                "Educational feedback must be fact-based with measurements only. " +
                "Full text: %s",
                fieldDescription,
                foundWord,
                text
            ));
        }
    }

    private TestJson createTestResults(int total, int passed, int failed, int skipped) {
        TestJson testJson = new TestJson();
        testJson.summary = new TestSummary(total, passed, failed, skipped);
        testJson.tests = List.of();
        testJson.failures = List.of();
        return testJson;
    }
}
