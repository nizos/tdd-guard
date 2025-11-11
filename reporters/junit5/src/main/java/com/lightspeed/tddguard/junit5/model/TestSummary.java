package com.lightspeed.tddguard.junit5.model;

/**
 * Test execution summary with counts.
 */
public class TestSummary {
    public int total;
    public int passed;
    public int failed;
    public int skipped;

    public TestSummary() {
    }

    public TestSummary(int total, int passed, int failed, int skipped) {
        this.total = total;
        this.passed = passed;
        this.failed = failed;
        this.skipped = skipped;
    }
}
