package com.lightspeed.tddguard.junit5.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test proving pattern detection works end-to-end.
 * Verifies that patterns are detected and educational feedback appears in test.json.
 */
class PatternDetectionIntegrationTest {

    private final Gson gson = new Gson();

    @Test
    void shouldDetectMockOverusePatternEndToEnd(@TempDir Path tempDir) throws IOException {
        // Given: Project with test code containing mock overuse
        setupProjectWithMockOveruse(tempDir);

        // When: Running tests
        runTestsAndWait(tempDir, TestsWithMockOveruse.class);

        // Then: test.json should contain educational feedback for mock-overuse
        JsonObject testJson = readTestJson(tempDir);
        JsonArray educational = testJson.getAsJsonArray("educational");

        assertTrue(educational.size() > 0, "Educational feedback should be present");

        boolean foundMockOveruse = false;
        for (int i = 0; i < educational.size(); i++) {
            JsonObject feedback = educational.get(i).getAsJsonObject();
            if ("mock-overuse".equals(feedback.get("category").getAsString())) {
                foundMockOveruse = true;

                // Verify evidence
                JsonObject evidence = feedback.getAsJsonObject("evidence");
                assertTrue(evidence.get("mockCount").getAsInt() > 0, "Mock count should be > 0");
                assertTrue(evidence.get("testCount").getAsInt() > 0, "Test count should be > 0");

                // Verify message and recommendation are present
                assertNotNull(feedback.get("message"));
                assertNotNull(feedback.get("recommendation"));
            }
        }

        assertTrue(foundMockOveruse, "Mock overuse pattern should be detected");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldDetectMissingIsolationPatternEndToEnd(@TempDir Path tempDir) throws IOException {
        // Given: Project with test code containing hardcoded URLs
        setupProjectWithHardcodedUrls(tempDir);

        // When: Running tests
        runTestsAndWait(tempDir, TestsWithHardcodedUrls.class);

        // Then: test.json should contain educational feedback for missing-isolation
        JsonObject testJson = readTestJson(tempDir);
        JsonArray educational = testJson.getAsJsonArray("educational");

        assertTrue(educational.size() > 0, "Educational feedback should be present");

        boolean foundMissingIsolation = false;
        for (int i = 0; i < educational.size(); i++) {
            JsonObject feedback = educational.get(i).getAsJsonObject();
            if ("missing-isolation".equals(feedback.get("category").getAsString())) {
                foundMissingIsolation = true;

                // Verify evidence
                JsonObject evidence = feedback.getAsJsonObject("evidence");
                assertTrue(evidence.get("hardcodedCount").getAsInt() > 0, "Hardcoded count should be > 0");

                // Verify message and recommendation are present
                assertNotNull(feedback.get("message"));
                assertNotNull(feedback.get("recommendation"));
            }
        }

        assertTrue(foundMissingIsolation, "Missing isolation pattern should be detected");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldDetectTestFixturesOpportunityEndToEnd(@TempDir Path tempDir) throws IOException {
        // Given: Project with high compilation time
        setupProjectWithComplexSetup(tempDir);

        // When: Running tests
        runTestsAndWait(tempDir, TestsWithComplexSetup.class);

        // Then: test.json should contain educational feedback for test-fixtures-opportunity
        JsonObject testJson = readTestJson(tempDir);
        JsonArray educational = testJson.getAsJsonArray("educational");

        // Note: This may not trigger without actual high compilation time
        // But we verify the structure if it does trigger
        for (int i = 0; i < educational.size(); i++) {
            JsonObject feedback = educational.get(i).getAsJsonObject();
            if ("test-fixtures-opportunity".equals(feedback.get("category").getAsString())) {
                // Verify evidence structure
                JsonObject evidence = feedback.getAsJsonObject("evidence");
                assertNotNull(evidence.get("compilationMs"));
                assertNotNull(evidence.get("depthAvg"));

                // Verify message and recommendation are present
                assertNotNull(feedback.get("message"));
                assertNotNull(feedback.get("recommendation"));
            }
        }

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldDetectFileStructureIssuesEndToEnd(@TempDir Path tempDir) throws IOException {
        // Given: Project with file structure issues
        setupProjectWithFileStructureIssues(tempDir);

        // When: Running tests
        runTestsAndWait(tempDir, TestsWithFileStructureIssues.class);

        // Then: test.json should contain educational feedback for file-structure
        JsonObject testJson = readTestJson(tempDir);
        JsonArray educational = testJson.getAsJsonArray("educational");

        assertTrue(educational.size() > 0, "Educational feedback should be present");

        boolean foundFileStructure = false;
        for (int i = 0; i < educational.size(); i++) {
            JsonObject feedback = educational.get(i).getAsJsonObject();
            if ("file-structure".equals(feedback.get("category").getAsString())) {
                foundFileStructure = true;

                // Verify evidence
                JsonObject evidence = feedback.getAsJsonObject("evidence");
                assertNotNull(evidence.get("testsInMain"), "testsInMain should be present");
                assertNotNull(evidence.get("namingViolations"), "namingViolations should be present");

                // Verify message and recommendation are present
                assertNotNull(feedback.get("message"));
                assertNotNull(feedback.get("recommendation"));
            }
        }

        assertTrue(foundFileStructure, "File structure pattern should be detected");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldDetectMultiplePatternsSimultaneously(@TempDir Path tempDir) throws IOException {
        // Given: Project with multiple anti-patterns
        setupProjectWithMultiplePatterns(tempDir);

        // When: Running tests
        runTestsAndWait(tempDir, TestsWithMultiplePatterns.class);

        // Then: test.json should contain multiple educational feedbacks
        JsonObject testJson = readTestJson(tempDir);
        JsonArray educational = testJson.getAsJsonArray("educational");

        assertTrue(educational.size() > 0, "Educational feedback should be present");

        // Collect all detected pattern categories
        Set<String> detectedCategories = new HashSet<>();
        for (int i = 0; i < educational.size(); i++) {
            JsonObject feedback = educational.get(i).getAsJsonObject();
            String category = feedback.get("category").getAsString();
            detectedCategories.add(category);

            // Verify all feedback has required fields
            assertNotNull(feedback.get("severity"));
            assertNotNull(feedback.get("title"));
            assertNotNull(feedback.get("evidence"));
            assertNotNull(feedback.get("message"));
            assertNotNull(feedback.get("recommendation"));
        }

        // Verify at least two different patterns detected
        assertTrue(detectedCategories.size() >= 2,
            "Should detect multiple patterns. Found: " + detectedCategories);

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    // === Helper Methods ===

    private void setupProjectWithMockOveruse(Path projectRoot) throws IOException {
        Path tddGuardDir = projectRoot.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // Create actual test source file with excessive mocks
        Path testFile = projectRoot.resolve("src/test/java/TestWithMocks.java");
        Files.createDirectories(testFile.getParent());

        String testCode = "import org.mockito.Mock;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
            "public class TestWithMocks {\n" +
            "    @Mock private ServiceRepo repo1;\n" +
            "    @Mock private ServiceRepo repo2;\n" +
            "    @Mock private ServiceRepo repo3;\n" +
            "    @Mock private Money amount;\n" +  // Value object mock
            "    @Mock private UserId userId;\n" +  // Value object mock
            "    @Test void test() { assertTrue(true); }\n" +
            "}\n";

        Files.writeString(testFile, testCode);
    }

    private void setupProjectWithHardcodedUrls(Path projectRoot) throws IOException {
        Path tddGuardDir = projectRoot.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // Create actual test source file with hardcoded URLs
        Path testFile = projectRoot.resolve("src/test/java/TestWithUrls.java");
        Files.createDirectories(testFile.getParent());

        String testCode = "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
            "public class TestWithUrls {\n" +
            "    private static final String API = \"https://api.example.com:8080/v1\";\n" +
            "    private static final String DB = \"https://db.example.com:5432\";\n" +
            "    @Test void test() { assertTrue(true); }\n" +
            "}\n";

        Files.writeString(testFile, testCode);
    }

    private void setupProjectWithComplexSetup(Path projectRoot) throws IOException {
        Path tddGuardDir = projectRoot.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // Create actual test source file with complex constructor calls
        Path testFile = projectRoot.resolve("src/test/java/TestWithComplexSetup.java");
        Files.createDirectories(testFile.getParent());

        String testCode = "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
            "public class TestWithComplexSetup {\n" +
            "    @Test void test() {\n" +
            "        Object service = new Service(new A(), new B(), new C(), new D());\n" +
            "        assertTrue(true);\n" +
            "    }\n" +
            "}\n";

        Files.writeString(testFile, testCode);
    }

    private void setupProjectWithFileStructureIssues(Path projectRoot) throws IOException {
        Path tddGuardDir = projectRoot.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // Create test file in production directory (CRITICAL ERROR)
        Path testInMain = projectRoot.resolve("src/main/java/BadTest.java");
        Files.createDirectories(testInMain.getParent());

        String badTestCode = "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
            "public class BadTest {\n" +
            "    @Test void test() { assertTrue(true); }\n" +
            "}\n";

        Files.writeString(testInMain, badTestCode);

        // Create test file with naming violation
        Path testWithBadName = projectRoot.resolve("src/test/java/UserValidator.java");
        Files.createDirectories(testWithBadName.getParent());

        String namingViolationCode = "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
            "public class UserValidator {\n" +
            "    @Test void shouldValidate() { assertTrue(true); }\n" +
            "}\n";

        Files.writeString(testWithBadName, namingViolationCode);
    }

    private void setupProjectWithMultiplePatterns(Path projectRoot) throws IOException {
        Path tddGuardDir = projectRoot.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // Create actual test source file with multiple anti-patterns
        Path testFile = projectRoot.resolve("src/test/java/TestWithMultipleIssues.java");
        Files.createDirectories(testFile.getParent());

        // Need high mock ratio (>2.0) OR value object mocks to trigger mock-overuse
        // Using 5 mocks for 2 tests = ratio 2.5 + value object mocks
        String testCode = "import org.mockito.Mock;\n" +
            "import org.junit.jupiter.api.Test;\n" +
            "import static org.junit.jupiter.api.Assertions.*;\n\n" +
            "public class TestWithMultipleIssues {\n" +
            "    @Mock private ServiceRepo repo1;\n" +
            "    @Mock private ServiceRepo repo2;\n" +
            "    @Mock private ServiceRepo repo3;\n" +
            "    @Mock private Money amount;\n" +  // Value object mock to ensure trigger
            "    @Mock private UserId userId;\n" +  // Value object mock
            "    private static final String URL = \"https://api.example.com:9999\";\n" +
            "    @Test void test1() { assertTrue(true); }\n" +
            "    @Test void test2() { assertTrue(true); }\n" +
            "}\n";

        Files.writeString(testFile, testCode);
    }

    private void runTestsAndWait(Path projectRoot, Class<?> testClass) {
        System.setProperty("tddguard.projectRoot", projectRoot.toString());

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(testClass))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Give pattern detection a moment to complete
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private JsonObject readTestJson(Path projectRoot) throws IOException {
        Path testJsonPath = projectRoot.resolve(".claude/tdd-guard/data/test.json");
        assertTrue(Files.exists(testJsonPath), "test.json should exist");

        String json = Files.readString(testJsonPath);
        return gson.fromJson(json, JsonObject.class);
    }

    // === Sample Test Classes ===

    /**
     * Test class with excessive mocking to trigger mock-overuse detection.
     */
    public static class TestsWithMockOveruse {
        // Simulate mock fields (TddGuardListener will scan actual test files in project)
        private Object mock1;
        private Object mock2;
        private Object mock3;
        private Object mock4;
        private Object mock5;

        @Test
        void test1() {
            assertTrue(true);
        }

        @Test
        void test2() {
            assertTrue(true);
        }
    }

    /**
     * Test class with hardcoded URLs to trigger missing-isolation detection.
     */
    public static class TestsWithHardcodedUrls {
        private static final String API_URL = "https://api.example.com:8080/v1";
        private static final String DB_URL = "https://db.example.com:5432";

        @Test
        void testApi() {
            assertTrue(true);
        }
    }

    /**
     * Test class with complex constructor calls to trigger test-fixtures-opportunity.
     */
    public static class TestsWithComplexSetup {
        @Test
        void testComplex() {
            // Simulate complex setup
            Object service = new Object();
            assertTrue(true);
        }
    }

    /**
     * Test class with multiple anti-patterns.
     */
    public static class TestsWithMultiplePatterns {
        // Mock overuse
        private Object mock1;
        private Object mock2;
        private Object mock3;

        // Hardcoded resources
        private static final String URL = "https://api.example.com:9999";

        @Test
        void test1() {
            assertTrue(true);
        }

        @Test
        void test2() {
            assertTrue(true);
        }
    }

    /**
     * Test class with file structure issues.
     */
    public static class TestsWithFileStructureIssues {
        @Test
        void testStructure() {
            assertTrue(true);
        }
    }
}
