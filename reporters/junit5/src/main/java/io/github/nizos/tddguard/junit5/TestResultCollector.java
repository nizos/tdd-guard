package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestError;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects test outcomes and groups them into {@link TestModule}s keyed by
 * moduleId (fully qualified class name). Pure data layer with no dependency
 * on the JUnit5 Platform API so it is directly unit-testable.
 */
public final class TestResultCollector {
    private final Map<String, List<TestCase>> moduleMap = new LinkedHashMap<>();

    public void recordPassed(String moduleId, String methodName) {
        add(moduleId, TestCase.passed(methodName, fullName(moduleId, methodName)));
    }

    public void recordFailed(String moduleId, String methodName, Throwable throwable) {
        add(moduleId, TestCase.failed(methodName,
                fullName(moduleId, methodName),
                buildErrors(throwable)));
    }

    public void recordSkipped(String moduleId, String methodName) {
        add(moduleId, TestCase.skipped(methodName, fullName(moduleId, methodName)));
    }

    public TestResult build() {
        List<TestModule> modules = new ArrayList<>();
        boolean anyFailed = false;

        for (Map.Entry<String, List<TestCase>> entry : moduleMap.entrySet()) {
            modules.add(new TestModule(entry.getKey(), entry.getValue()));
            for (TestCase test : entry.getValue()) {
                if (test.state() == TestCase.State.FAILED) {
                    anyFailed = true;
                }
            }
        }

        return new TestResult(modules, anyFailed ? "failed" : "passed");
    }

    private void add(String moduleId, TestCase testCase) {
        moduleMap.computeIfAbsent(moduleId, key -> new ArrayList<>()).add(testCase);
    }

    private static String fullName(String moduleId, String methodName) {
        return moduleId + "::" + methodName;
    }

    private static List<TestError> buildErrors(Throwable throwable) {
        if (throwable == null) {
            return Collections.singletonList(new TestError("Test failed without a throwable"));
        }
        String message = throwable.getMessage();
        if (message == null) {
            message = throwable.getClass().getName();
        }
        return Collections.singletonList(new TestError(message));
    }
}
