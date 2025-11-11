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
 * Tests for MockOveruseDetector.
 * Validates fact-based detection of mock overuse patterns.
 */
class MockOveruseDetectorTest {

    private MockOveruseDetector createDetector(Path projectRoot) {
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(projectRoot, key -> null, key -> null);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);
        return new MockOveruseDetector(analyzer);
    }

    @Test
    void shouldReturnCorrectCategory(@TempDir Path projectRoot) {
        MockOveruseDetector detector = createDetector(projectRoot);
        assertEquals("mock-overuse", detector.getCategory());
    }

    @Test
    void shouldDetectHighMockRatio(@TempDir Path projectRoot) throws IOException {
        // Given: 50 mocks across 20 tests (ratio 2.5 > threshold 2.0)
        MockOveruseDetector detector = createDetector(projectRoot);
        createTestFileWithMocks(projectRoot, "Test1.java", 25);
        createTestFileWithMocks(projectRoot, "Test2.java", 25);

        TestJson testResults = createTestResults(20, 20, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals("mock-overuse", feedback.get().category);
        assertEquals("warning", feedback.get().severity);

        Map<String, Object> evidence = feedback.get().evidence;
        assertEquals(50, evidence.get("mockCount"));
        assertEquals(20, evidence.get("testCount"));
        assertEquals("2.50", evidence.get("ratio"));
    }

    @Test
    void shouldNotDetectBelowThreshold(@TempDir Path projectRoot) throws IOException {
        // Given: 30 mocks across 20 tests (ratio 1.5 < threshold 2.0)
        MockOveruseDetector detector = createDetector(projectRoot);
        createTestFileWithMocks(projectRoot, "Test1.java", 15);
        createTestFileWithMocks(projectRoot, "Test2.java", 15);

        TestJson testResults = createTestResults(20, 20, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertFalse(feedback.isPresent());
    }

    @Test
    void shouldDetectValueObjectMocks(@TempDir Path projectRoot) throws IOException {
        // Given: Low ratio but value object mocks present
        MockOveruseDetector detector = createDetector(projectRoot);
        String testCode = "import org.mockito.Mock;\n\n" +
            "public class OrderTest {\n" +
            "    @Mock\n" +
            "    private UserId userId;\n\n" +
            "    @Mock\n" +
            "    private OrderId orderId;\n\n" +
            "    @Test\n" +
            "    void test() {}\n" +
            "}\n";

        Path testFile = projectRoot.resolve("src/test/java/OrderTest.java");
        Files.createDirectories(testFile.getParent());
        Files.writeString(testFile, testCode);

        TestJson testResults = createTestResults(10, 10, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());

        @SuppressWarnings("unchecked")
        List<String> valueObjectMocks = (List<String>) feedback.get().evidence.get("valueObjectMocks");
        assertTrue(valueObjectMocks.contains("UserId"));
        assertTrue(valueObjectMocks.contains("OrderId"));
    }

    @Test
    void shouldCountMockAnnotations(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "import org.mockito.Mock;\n\n" +
            "public class ServiceTest {\n" +
            "    @Mock\n" +
            "    private Repository repo;\n\n" +
            "    @Mock\n" +
            "    private Cache cache;\n\n" +
            "    @Mock\n" +
            "    private Logger logger;\n" +
            "}\n";

        createTestFile(projectRoot, "ServiceTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals(3, feedback.get().evidence.get("mockCount"));
    }

    @Test
    void shouldCountMockBeanAnnotations(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "import org.springframework.boot.test.mock.mockito.MockBean;\n\n" +
            "@SpringBootTest\n" +
            "public class IntegrationTest {\n" +
            "    @MockBean\n" +
            "    private ExternalService service1;\n\n" +
            "    @MockBean\n" +
            "    private ExternalService service2;\n\n" +
            "    @MockBean\n" +
            "    private ExternalService service3;\n" +
            "}\n";

        createTestFile(projectRoot, "IntegrationTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals(3, feedback.get().evidence.get("mockCount"));
    }

    @Test
    void shouldCountMockitoDotMockCalls(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "import static org.mockito.Mockito.*;\n\n" +
            "public class LegacyTest {\n" +
            "    @Test\n" +
            "    void testOldStyle() {\n" +
            "        Service s1 = mock(Service.class);\n" +
            "        Service s2 = mock(Service.class);\n" +
            "        Repository r = mock(Repository.class);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "LegacyTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals(3, feedback.get().evidence.get("mockCount"));
    }

    @Test
    void shouldCombineAllMockTypes(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "import org.mockito.Mock;\n" +
            "import org.springframework.boot.test.mock.mockito.MockBean;\n" +
            "import static org.mockito.Mockito.*;\n\n" +
            "public class MixedTest {\n" +
            "    @Mock\n" +
            "    private Service service1;\n\n" +
            "    @MockBean\n" +
            "    private Service service2;\n\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        Service service3 = mock(Service.class);\n" +
            "    }\n" +
            "}\n";

        createTestFile(projectRoot, "MixedTest.java", testCode);

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        assertEquals(3, feedback.get().evidence.get("mockCount"));
    }

    @Test
    void shouldHandleNoMocks(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        String testCode = "public class CleanTest {\n" +
            "    private final Service service = new RealService();\n\n" +
            "    @Test\n" +
            "    void test() {\n" +
            "        assertEquals(42, service.compute());\n" +
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
    void shouldIncludeExampleFileInEvidence(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        createTestFileWithValueObjectMock(projectRoot, "ExampleTest.java");

        TestJson testResults = createTestResults(1, 1, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String exampleFile = (String) feedback.get().evidence.get("exampleFile");
        assertNotNull(exampleFile);
        assertTrue(exampleFile.contains("ExampleTest.java"));
    }

    @Test
    void shouldProvideMeaningfulMessage(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        createTestFileWithMocks(projectRoot, "Test.java", 50);

        TestJson testResults = createTestResults(20, 20, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then
        assertTrue(feedback.isPresent());
        String message = feedback.get().message;
        assertTrue(message.contains("50"));  // mock count
        assertTrue(message.contains("20"));  // test count
        assertTrue(message.contains("2.50")); // ratio
    }

    @Test
    void shouldProvideRecommendation(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        createTestFileWithMocks(projectRoot, "Test.java", 50);

        TestJson testResults = createTestResults(20, 20, 0, 0);
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
    void shouldHandleZeroTests(@TempDir Path projectRoot) throws IOException {
        MockOveruseDetector detector = createDetector(projectRoot);
        // Given
        createTestFileWithMocks(projectRoot, "Test.java", 10);

        TestJson testResults = createTestResults(0, 0, 0, 0);
        BuildMetrics buildMetrics = BuildMetrics.empty();

        // When
        Optional<EducationalFeedback> feedback = detector.detect(testResults, projectRoot, buildMetrics);

        // Then - Should not crash, should not detect (no tests to have ratio)
        assertFalse(feedback.isPresent());
    }

    // === Helper Methods ===

    private void createTestFileWithMocks(Path projectRoot, String filename, int mockCount) throws IOException {
        StringBuilder code = new StringBuilder();
        code.append("import org.mockito.Mock;\n\n");
        code.append("public class ").append(filename.replace(".java", "")).append(" {\n");

        for (int i = 0; i < mockCount; i++) {
            code.append("    @Mock\n");
            code.append("    private ServiceRepository service").append(i).append(";\n\n");
        }

        code.append("}\n");

        createTestFile(projectRoot, filename, code.toString());
    }

    private void createTestFileWithValueObjectMock(Path projectRoot, String filename) throws IOException {
        String className = filename.replace(".java", "");
        String code = "import org.mockito.Mock;\n\n" +
            "public class " + className + " {\n" +
            "    @Mock\n" +
            "    private UserId userId;\n" +
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
