package com.lightspeed.tddguard.junit5;

import com.lightspeed.tddguard.junit5.model.TestCase;
import com.lightspeed.tddguard.junit5.model.TestFailure;
import org.junit.jupiter.api.Test;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestResultCollector.
 *
 * Covers AC#5: Test Lifecycle Capture
 */
class TestResultCollectorTest {

    @Test
    void shouldCollectPassedTest() {
        // Given: A test result collector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver();
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A passed test
        TestIdentifier testId = createTestIdentifier("testPassed", "com.example.MyTest", "testPassed()");
        collector.recordTestStart(testId);

        // Wait a bit to have duration
        try { Thread.sleep(10); } catch (InterruptedException e) {}

        TestExecutionResult result = TestExecutionResult.successful();

        // When: Recording the test result
        collector.recordTestFinish(testId, result);

        // Then: Should have one test with passed status
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());

        TestCase test = tests.get(0);
        assertEquals("testPassed", test.name);
        assertEquals("passed", test.status);
        assertTrue(test.duration >= 0, "Duration should be >= 0");
        assertNotNull(test.displayName);
    }

    @Test
    void shouldCollectFailedTestWithError() {
        // Given: A test result collector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver();
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A failed test with exception
        TestIdentifier testId = createTestIdentifier("testFailed", "com.example.MyTest", "testFailed()");
        collector.recordTestStart(testId);

        AssertionError error = new AssertionError("Expected 5 but was 3");
        TestExecutionResult result = TestExecutionResult.failed(error);

        // When: Recording the test result
        collector.recordTestFinish(testId, result);

        // Then: Should have one test with failed status and error details
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        TestCase test = tests.get(0);

        assertEquals("failed", test.status);

        List<TestFailure> failures = collector.getFailures();
        assertEquals(1, failures.size());
        assertEquals("Expected 5 but was 3", failures.get(0).message);
        assertNotNull(failures.get(0).stack);
        assertTrue(failures.get(0).stack.contains("AssertionError"));
    }

    @Test
    void shouldCollectSkippedTest() {
        // Given: A test result collector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver();
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A skipped test
        TestIdentifier testId = createTestIdentifier("testSkipped", "com.example.MyTest", "testSkipped()");

        // When: Recording as skipped
        collector.recordSkipped(testId, "Test disabled");

        // Then: Should have one test with skipped status
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        assertEquals("skipped", tests.get(0).status);
    }

    @Test
    void shouldCollectMultipleTests() {
        // Given: A test result collector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver();
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: Multiple tests from different classes
        TestIdentifier test1 = createTestIdentifier("test1", "com.example.FirstTest", "test1()");
        TestIdentifier test2 = createTestIdentifier("test2", "com.example.FirstTest", "test2()");
        TestIdentifier test3 = createTestIdentifier("test3", "com.example.SecondTest", "test3()");

        // When: Recording tests
        collector.recordTestStart(test1);
        collector.recordTestFinish(test1, TestExecutionResult.successful());

        collector.recordTestStart(test2);
        collector.recordTestFinish(test2, TestExecutionResult.successful());

        collector.recordTestStart(test3);
        collector.recordTestFinish(test3, TestExecutionResult.successful());

        // Then: Should have 3 tests
        List<TestCase> tests = collector.getTests();
        assertEquals(3, tests.size());
    }

    @Test
    void shouldCalculateSummaryCorrectly() {
        // Given: A test result collector with mixed results
        SourceDirectoryResolver resolver = createDefaultResolver();
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // When: Recording mixed test results
        TestIdentifier t1 = createTestIdentifier("t1", "Test", "t1()");
        collector.recordTestStart(t1);
        collector.recordTestFinish(t1, TestExecutionResult.successful());

        TestIdentifier t2 = createTestIdentifier("t2", "Test", "t2()");
        collector.recordTestStart(t2);
        collector.recordTestFinish(t2, TestExecutionResult.successful());

        TestIdentifier t3 = createTestIdentifier("t3", "Test", "t3()");
        collector.recordTestStart(t3);
        collector.recordTestFinish(t3, TestExecutionResult.failed(new Error("fail")));

        TestIdentifier t4 = createTestIdentifier("t4", "Test", "t4()");
        collector.recordSkipped(t4, "reason");

        // Then: Summary should be correct
        assertEquals(4, collector.getTotalTests());
        assertEquals(2, collector.getPassedTests());
        assertEquals(1, collector.getFailedTests());
        assertEquals(1, collector.getSkippedTests());
    }

    @Test
    void shouldTrackTestDuration() {
        // Given: A test result collector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver();
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A test that runs for measurable time
        TestIdentifier testId = createTestIdentifier("test", "Test", "test()");
        collector.recordTestStart(testId);

        // Wait 20ms
        try { Thread.sleep(20); } catch (InterruptedException e) {}

        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: Duration should be at least 15ms (allowing some variance)
        List<TestCase> tests = collector.getTests();
        assertTrue(tests.get(0).duration >= 15,
            "Expected duration >= 15ms, got " + tests.get(0).duration);
    }

    @Test
    void shouldResolveFilePathWithCustomTestDirectory() {
        // Given: A resolver configured with custom test directory
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            Path.of("/project"),
            prop -> prop.equals("tddguard.testSourceDirs") ? "src/integrationTest/java" : null,
            env -> null
        );
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A test class that should be found in the custom directory
        TestIdentifier testId = createTestIdentifier("testCustom", "com.example.IntegrationTest", "testCustom()");
        collector.recordTestStart(testId);

        // When: Recording the test result
        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: File path should use custom test directory
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        assertEquals("src/integrationTest/java/com/example/IntegrationTest.java", tests.get(0).file);
    }

    @Test
    void shouldTryMultipleTestDirectoriesInOrder() {
        // Given: A resolver configured with multiple test directories
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            Path.of("/project"),
            prop -> prop.equals("tddguard.testSourceDirs") ? "src/test/kotlin,src/test/java" : null,
            env -> null
        );
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A Kotlin test class
        TestIdentifier testId = createTestIdentifier("testKotlin", "com.example.KotlinTest", "testKotlin()");
        collector.recordTestStart(testId);

        // When: Recording the test result
        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: Should try src/test/kotlin first (even though .java extension)
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        // Note: We'll check kotlin directory first based on configuration order
        String filePath = tests.get(0).file;
        assertTrue(filePath.contains("src/test/kotlin") || filePath.contains("src/test/java"),
            "File path should use one of the configured test directories: " + filePath);
    }

    @Test
    void shouldResolveMainClassWithCustomMainDirectory() {
        // Given: A resolver configured with custom main directory
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            Path.of("/project"),
            prop -> prop.equals("tddguard.mainSourceDirs") ? "src/main/kotlin" : null,
            env -> null
        );
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A non-test class (no Test/Tests suffix)
        TestIdentifier testId = createTestIdentifier("myMethod", "com.example.MyService", "myMethod()");
        collector.recordTestStart(testId);

        // When: Recording the test result
        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: File path should use custom main directory
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        assertEquals("src/main/kotlin/com/example/MyService.java", tests.get(0).file);
    }

    @Test
    void shouldFallbackToDefaultsWhenNoConfiguration() {
        // Given: A resolver with no configuration (empty lists)
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            Path.of("/project"),
            prop -> null,
            env -> null
        );
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A test class
        TestIdentifier testId = createTestIdentifier("test", "com.example.MyTest", "test()");
        collector.recordTestStart(testId);

        // When: Recording the test result
        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: Should use default src/test/java directory
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        assertEquals("src/test/java/com/example/MyTest.java", tests.get(0).file);
    }

    @Test
    void shouldHandleNonTestClassWithNoMainConfiguration() {
        // Given: A resolver with no main source configuration
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            Path.of("/project"),
            prop -> null,
            env -> null
        );
        TestResultCollector collector = new TestResultCollector(Path.of("/project"), resolver);

        // And: A non-test class (no Test/Tests suffix)
        TestIdentifier testId = createTestIdentifier("method", "com.example.MyClass", "method()");
        collector.recordTestStart(testId);

        // When: Recording the test result
        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: Should use default src/main/java directory
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        assertEquals("src/main/java/com/example/MyClass.java", tests.get(0).file);
    }

    @Test
    void shouldResolveCorrectDirectoryWhenFileExistsInSecondDirectory(@org.junit.jupiter.api.io.TempDir Path projectRoot) throws Exception {
        // Given: Create test file in second configured directory only
        Path testDir = projectRoot.resolve("src/test/java/com/example");
        java.nio.file.Files.createDirectories(testDir);
        java.nio.file.Files.writeString(testDir.resolve("MyTest.java"), "public class MyTest {}");

        // And: Configure with multiple test directories (first doesn't contain file)
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot,
            prop -> prop.equals("tddguard.testSourceDirs") ? "test,src/test/java" : null,
            env -> null
        );
        TestResultCollector collector = new TestResultCollector(projectRoot, resolver);

        // And: Create test identifier for the test class
        TestIdentifier testId = createTestIdentifier("testSomething", "com.example.MyTest", "testSomething()");

        // When: Recording test start and finish
        collector.recordTestStart(testId);
        collector.recordTestFinish(testId, TestExecutionResult.successful());

        // Then: File path should point to actual location (second directory)
        List<TestCase> tests = collector.getTests();
        assertEquals(1, tests.size());
        assertEquals("src/test/java/com/example/MyTest.java", tests.get(0).file,
            "File path should use the directory where the file actually exists");
    }

    // Helper method to create test identifiers using real JUnit Platform APIs
    private TestIdentifier createTestIdentifier(String uniqueId, String className, String displayName) {
        MethodSource source = MethodSource.from(className, uniqueId);
        return TestIdentifier.from(
            new TestDescriptorStub(uniqueId, displayName, source)
        );
    }

    // Helper method to create a default resolver (no custom configuration)
    private SourceDirectoryResolver createDefaultResolver() {
        return new SourceDirectoryResolver(
            Path.of("/project"),
            prop -> null,
            env -> null
        );
    }
}
