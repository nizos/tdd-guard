package com.lightspeed.tddguard.junit5.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sample test class used by integration tests.
 * Contains passed, failed, and skipped tests.
 */
class SampleTests {

    @Test
    void testThatPasses() {
        assertEquals(2, 1 + 1, "Math should work");
    }

    @Test
    void testThatFails() {
        fail("This test intentionally fails");
    }

    @Test
    @Disabled("Intentionally skipped for integration test")
    void testThatIsSkipped() {
        // This should not execute
    }
}
