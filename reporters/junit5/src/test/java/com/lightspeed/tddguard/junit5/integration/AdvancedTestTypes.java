package com.lightspeed.tddguard.junit5.integration;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.DynamicTest;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Sample tests demonstrating all JUnit 5 test types for AC#7.
 */
class AdvancedTestTypes {

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3})
    void parameterizedTest(int value) {
        assertTrue(value > 0);
    }

    @RepeatedTest(3)
    void repeatedTest() {
        assertTrue(true);
    }

    @TestFactory
    Collection<DynamicTest> dynamicTests() {
        return Arrays.asList(
            dynamicTest("Dynamic test 1", () -> assertTrue(true)),
            dynamicTest("Dynamic test 2", () -> assertTrue(true))
        );
    }
}
