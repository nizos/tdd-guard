package com.lightspeed.tddguard.junit5.integration;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sample test with @Nested structure for AC#7 verification.
 */
class NestedTestExample {

    @Test
    void outerTest() {
        assertTrue(true);
    }

    @Nested
    class InnerTests {

        @Test
        void innerTest() {
            assertTrue(true);
        }
    }
}
