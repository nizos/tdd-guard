package com.lightspeed.tddguard.junit5.model;

import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;

import java.util.List;

/**
 * Root object for test.json output.
 * Matches TDD Guard JSON schema exactly.
 */
public class TestJson {
    public String framework;
    public String timestamp;
    public long duration;
    public TestSummary summary;
    public List<TestCase> tests;
    public List<TestFailure> failures;
    public List<EducationalFeedback> educational;

    public TestJson() {
        this.framework = "junit5";
        this.educational = List.of();
    }
}
