package io.github.nizos.tddguard.junit5.fixtures;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Fixture driven by {@code LauncherIntegrationTest} via the JUnit Platform Launcher API.
 * Not part of the regular test suite — excluded from {@code ./gradlew test} via
 * {@code tasks.test.exclude} in {@code build.gradle.kts}.
 *
 * <p>The {@code @BeforeAll} calls {@code Assumptions.assumeTrue(false)}, which aborts the
 * container before any child test runs. From the platform's perspective the container finishes
 * {@code ABORTED} and no child events fire.
 */
public class AbortedContainerFixture {

    @BeforeAll
    static void setupThatAborts() {
        Assumptions.assumeTrue(false, "container aborted intentionally by fixture");
    }

    @Test
    void firstTest() {
    }

    @Test
    void secondTest() {
    }
}
