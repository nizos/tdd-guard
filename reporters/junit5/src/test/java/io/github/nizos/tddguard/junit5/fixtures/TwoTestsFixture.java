package io.github.nizos.tddguard.junit5.fixtures;

import org.junit.jupiter.api.Test;

/**
 * Minimal two-test fixture used by {@code TddGuardListenerTest} to verify that
 * {@code expectedCount} is correctly captured from a live {@code TestPlan}.
 *
 * <p>Excluded from the regular test suite via {@code tasks.test.exclude} in
 * {@code build.gradle.kts}; discovered on demand by the unit test.
 */
public class TwoTestsFixture {
    @Test
    public void testOne() {}

    @Test
    public void testTwo() {}
}
