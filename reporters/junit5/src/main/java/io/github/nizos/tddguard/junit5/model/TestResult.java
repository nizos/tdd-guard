package io.github.nizos.tddguard.junit5.model;

import java.util.List;
import java.util.Objects;

/**
 * Top-level test result written to test.json.
 * Matches the TDD Guard canonical schema defined in
 * src/contracts/schemas/reporterSchemas.ts.
 */
public final class TestResult {
    private final List<TestModule> testModules;
    private final String reason;

    public TestResult(List<TestModule> testModules) {
        this(testModules, null);
    }

    public TestResult(List<TestModule> testModules, String reason) {
        this.testModules = Objects.requireNonNull(testModules, "testModules must not be null");
        this.reason = reason;
    }

    public List<TestModule> testModules() {
        return testModules;
    }

    public String reason() {
        return reason;
    }
}
