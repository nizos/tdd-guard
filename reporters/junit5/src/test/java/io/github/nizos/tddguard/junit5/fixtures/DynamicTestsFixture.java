package io.github.nizos.tddguard.junit5.fixtures;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.stream.Stream;

/**
 * Fixture that generates two dynamic tests via {@code @TestFactory}. Dynamic tests are not
 * known at discovery time and are therefore not included in the initial {@code expectedCount}.
 * The listener handles this by incrementing {@code expectedCount} for each
 * {@code dynamicTestRegistered} callback, so the comparison stays meaningful.
 *
 * <p>Excluded from the regular test suite via {@code tasks.test.exclude} in
 * {@code build.gradle.kts}; driven by {@code LauncherIntegrationTest}.
 */
public class DynamicTestsFixture {
    @TestFactory
    Stream<DynamicTest> dynamicTests() {
        return Stream.of(
                DynamicTest.dynamicTest("first dynamic test", () -> {}),
                DynamicTest.dynamicTest("second dynamic test", () -> {})
        );
    }
}
