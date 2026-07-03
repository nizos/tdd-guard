package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestError;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;
import io.github.nizos.tddguard.junit5.model.UnhandledError;

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
    private int expectedCount = 0;
    private int recordedCount = 0;
    private boolean countTrustworthy = true;
    private final List<UnhandledError> unhandledErrors = new ArrayList<>();

    public void setExpectedCount(int count) {
        this.expectedCount = count;
    }

    public void incrementExpectedCount() {
        this.expectedCount++;
    }

    /**
     * Marks the expected count as untrustworthy for the rest of this run.
     *
     * <p>This is a one-way, sticky flag: once marked untrustworthy the interrupted
     * comparison is disabled even if more dynamic tests register afterwards.
     *
     * <p>Used when a container finishes {@code ABORTED} (e.g. {@code Assumptions.assumeTrue(false)}
     * in a {@code @BeforeAll}): the platform never fires events for that container's children,
     * so walking descendants would fabricate outcomes that were never assigned.
     */
    public void markCountUntrustworthy() {
        this.countTrustworthy = false;
    }

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

    public void recordUnhandledError(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        String name = throwable.getClass().getName();
        String message = throwable.getMessage();
        if (message == null) {
            message = name;
        }
        String stack = StackFilter.firstUserFrame(throwable);
        unhandledErrors.add(new UnhandledError(name, message, stack));
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

        String reason;
        if (anyFailed || !unhandledErrors.isEmpty()) {
            reason = "failed";
        } else if (countTrustworthy && expectedCount > 0 && recordedCount < expectedCount) {
            reason = "interrupted";
        } else {
            // An empty suite (expectedCount not set) emits "passed", not "interrupted".
            // This intentionally diverges from rspec, which treats empty runs as interrupted.
            // Also emits "passed" when countTrustworthy is false (e.g. an aborted container
            // made the count unreliable), mirroring minitest returning expected_count=0.
            reason = "passed";
        }

        return new TestResult(modules, reason, List.copyOf(unhandledErrors));
    }

    private void add(String moduleId, TestCase testCase) {
        moduleMap.computeIfAbsent(moduleId, key -> new ArrayList<>()).add(testCase);
        recordedCount++;
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
        String stack = StackFilter.firstUserFrame(throwable);
        return Collections.singletonList(new TestError(message, stack));
    }
}
