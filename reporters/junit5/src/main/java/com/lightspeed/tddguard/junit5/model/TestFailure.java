package com.lightspeed.tddguard.junit5.model;

/**
 * Test failure details with message and stack trace.
 */
public class TestFailure {
    public String name;
    public String file;
    public String message;
    public String stack;

    public TestFailure() {
    }

    public TestFailure(String name, String file, String message, String stack) {
        this.name = name;
        this.file = file;
        this.message = message;
        this.stack = stack;
    }
}
