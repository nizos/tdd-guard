package com.lightspeed.tddguard.junit5;

import com.lightspeed.tddguard.junit5.model.TestCase;
import com.lightspeed.tddguard.junit5.model.TestFailure;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Collects test execution results and builds test/failure lists.
 * Thread-safe to support JUnit Platform parallel test execution.
 */
class TestResultCollector {

    private final Path projectRoot;
    private final SourceDirectoryResolver directoryResolver;
    private final List<TestCase> tests = new CopyOnWriteArrayList<>();
    private final List<TestFailure> failures = new CopyOnWriteArrayList<>();
    private final Map<String, Long> testStartTimes = new ConcurrentHashMap<>();

    private volatile long testPlanStartTime;

    TestResultCollector(Path projectRoot, SourceDirectoryResolver directoryResolver) {
        this.projectRoot = projectRoot;
        this.directoryResolver = directoryResolver;
    }

    void testPlanStarted() {
        this.testPlanStartTime = System.currentTimeMillis();
    }

    void recordTestStart(TestIdentifier test) {
        if (!test.isTest()) return;
        testStartTimes.put(test.getUniqueIdObject().toString(), System.currentTimeMillis());
    }

    void recordTestFinish(TestIdentifier test, TestExecutionResult result) {
        if (!test.isTest()) return;

        String uniqueId = test.getUniqueIdObject().toString();
        long startTime = testStartTimes.getOrDefault(uniqueId, System.currentTimeMillis());
        long duration = System.currentTimeMillis() - startTime;

        String status = mapStatus(result.getStatus());
        String filePath = extractFilePath(test);
        String methodName = extractMethodName(test);

        TestCase testCase = new TestCase(
            methodName,
            filePath,
            status,
            duration,
            test.getDisplayName()
        );

        tests.add(testCase);

        // Record failure if test failed
        if (status.equals("failed") && result.getThrowable().isPresent()) {
            Throwable throwable = result.getThrowable().get();
            TestFailure failure = new TestFailure(
                methodName,
                filePath,
                throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName(),
                getStackTrace(throwable)
            );
            failures.add(failure);
        }
    }

    void recordSkipped(TestIdentifier test, String reason) {
        if (!test.isTest()) return;

        String filePath = extractFilePath(test);
        String methodName = extractMethodName(test);

        TestCase testCase = new TestCase(
            methodName,
            filePath,
            "skipped",
            0,
            test.getDisplayName()
        );

        tests.add(testCase);
    }

    List<TestCase> getTests() {
        return new ArrayList<>(tests);
    }

    List<TestFailure> getFailures() {
        return new ArrayList<>(failures);
    }

    long getTotalDuration() {
        return System.currentTimeMillis() - testPlanStartTime;
    }

    int getTotalTests() {
        return tests.size();
    }

    int getPassedTests() {
        return (int) tests.stream().filter(t -> t.status.equals("passed")).count();
    }

    int getFailedTests() {
        return (int) tests.stream().filter(t -> t.status.equals("failed")).count();
    }

    int getSkippedTests() {
        return (int) tests.stream().filter(t -> t.status.equals("skipped")).count();
    }

    private String mapStatus(TestExecutionResult.Status status) {
        switch (status) {
            case SUCCESSFUL:
                return "passed";
            case FAILED:
                return "failed";
            case ABORTED:
                return "skipped";
            default:
                return "unknown";
        }
    }

    private String extractFilePath(TestIdentifier test) {
        if (test.getSource().isEmpty()) {
            return "";
        }

        var source = test.getSource().get();

        // Try MethodSource first
        if (source instanceof MethodSource) {
            MethodSource methodSource = (MethodSource) source;
            String className = methodSource.getClassName();
            return classNameToFilePath(className);
        }

        // Try ClassSource
        if (source instanceof ClassSource) {
            ClassSource classSource = (ClassSource) source;
            String className = classSource.getClassName();
            return classNameToFilePath(className);
        }

        return "";
    }

    private String classNameToFilePath(String className) {
        // Convert com.example.MyTest to com/example/MyTest.java
        String relativePath = className.replace('.', '/') + ".java";

        // Determine if this is a test class
        boolean isTestClass = className.endsWith("Test") || className.endsWith("Tests");

        // Get configured directories from resolver
        List<String> directories;
        List<String> defaultDirectories;

        if (isTestClass) {
            directories = directoryResolver.resolveTestSourceDirs();
            // Default test directories if no configuration
            defaultDirectories = java.util.Arrays.asList("src/test/java", "src/test/kotlin");
        } else {
            directories = directoryResolver.resolveMainSourceDirs();
            // Default main directories if no configuration
            defaultDirectories = java.util.Arrays.asList("src/main/java", "src/main/kotlin");
        }

        // If no configuration, use defaults
        if (directories.isEmpty()) {
            directories = defaultDirectories;
        }

        // Try each directory and check if file exists
        for (String dir : directories) {
            Path fullPath = projectRoot.resolve(dir).resolve(relativePath);
            if (Files.exists(fullPath)) {
                return dir + "/" + relativePath;
            }
        }

        // Fallback to first directory if file not found in any location
        if (!directories.isEmpty()) {
            return directories.get(0) + "/" + relativePath;
        }

        // Fallback to original behavior (should never reach here with defaults above)
        if (isTestClass) {
            return "src/test/java/" + relativePath;
        }
        return "src/main/java/" + relativePath;
    }

    private String extractMethodName(TestIdentifier test) {
        if (test.getSource().isEmpty()) {
            return test.getDisplayName();
        }

        var source = test.getSource().get();
        if (source instanceof MethodSource) {
            MethodSource methodSource = (MethodSource) source;
            return methodSource.getMethodName();
        }

        return test.getDisplayName();
    }

    private String getStackTrace(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
