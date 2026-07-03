package io.github.nizos.tddguard.junit5.fixtures;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Fixture for verifying that a whole {@code @Disabled} class does not produce a false
 * {@code "interrupted"} reason. The JUnit Platform fires {@code executionSkipped} on the
 * container identifier and does not descend into the individual methods; the listener must
 * therefore record each descendant as skipped so {@code expectedCount} and
 * {@code recordedCount} stay balanced.
 *
 * <p>Excluded from the regular test suite via {@code tasks.test.exclude} in
 * {@code build.gradle.kts}; driven by {@code LauncherIntegrationTest}.
 */
@Disabled("entire class disabled for fixture purposes")
public class DisabledClassFixture {
    @Test
    void firstTest() {}

    @Test
    void secondTest() {}
}
