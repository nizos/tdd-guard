package com.lightspeed.tddguard.junit5.model;

/**
 * Individual test case result.
 */
public class TestCase {
    public String name;
    public String file;
    public String status;
    public long duration;
    public String displayName;

    public TestCase() {
    }

    public TestCase(String name, String file, String status, long duration, String displayName) {
        this.name = name;
        this.file = file;
        this.status = status;
        this.duration = duration;
        this.displayName = displayName;
    }
}
