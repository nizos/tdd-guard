package com.lightspeed.tddguard.junit5.integration;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * JUnit 4 test fixture used to verify JUnit Vintage Engine support.
 * Uses JUnit 4 annotations (@org.junit.Test) to ensure tests are executed
 * via the Vintage engine.
 */
public class JUnit4VintageFixture {

    @Test
    public void junit4TestThatPasses() {
        assertEquals("JUnit 4 test should pass", 2, 1 + 1);
    }

    @Test
    public void junit4TestThatFails() {
        fail("This JUnit 4 test intentionally fails");
    }
}
