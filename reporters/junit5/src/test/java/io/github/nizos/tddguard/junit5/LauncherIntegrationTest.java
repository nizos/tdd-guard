package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.fixtures.AbortedContainerFixture;
import io.github.nizos.tddguard.junit5.fixtures.DisabledClassFixture;
import io.github.nizos.tddguard.junit5.fixtures.DynamicTestsFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * In-process integration tests that cover JUnit Platform behaviours a pure unit test would
 * have to assume. Each test here exercises a scenario where the platform's callback sequence
 * differs from what hand-built {@code TestIdentifier}s in {@link TddGuardListenerTest} can
 * represent — for example, the way the platform fires {@code executionSkipped} on a container
 * rather than on individual tests when an entire class is {@code @Disabled}.
 *
 * <p>Follows the same bar as {@link UnhandledErrorsIntegrationTest}: an in-process case is
 * justified when it guards against platform API regressions that unit tests cannot detect.
 * Scenarios already covered by the collector unit tests or the cross-reporter Gradle harness
 * ({@code reporters/test/reporters.integration.test.ts}) are not duplicated here.
 *
 * <p>Fixture classes live under the {@code fixtures} package and are excluded from the regular
 * test suite via {@code tasks.test.exclude} in {@code build.gradle.kts}.
 */
class LauncherIntegrationTest {

    private static final String DATA_PATH = ".claude/tdd-guard/data/test.json";

    @Test
    void emitsPassedReasonForWholeDisabledClass(@TempDir Path tmp) throws Exception {
        TddGuardListener listener = createListener(tmp);

        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(DisabledClassFixture.class))
                .build();
        var launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        Path testJson = tmp.resolve(DATA_PATH);
        assertTrue(Files.exists(testJson),
                "test.json was not written — listener wiring may be broken");
        String content = Files.readString(testJson);
        // A whole @Disabled class must emit "passed", not "interrupted".
        // The platform fires executionSkipped on the container; the listener records each
        // descendant test as skipped so expectedCount and recordedCount stay balanced.
        assertTrue(content.contains("\"reason\": \"passed\""),
                "a whole @Disabled class should report passed, not interrupted");
    }

    @Test
    void emitsPassedReasonForDynamicTests(@TempDir Path tmp) throws Exception {
        TddGuardListener listener = createListener(tmp);

        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(DynamicTestsFixture.class))
                .build();
        var launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        Path testJson = tmp.resolve(DATA_PATH);
        assertTrue(Files.exists(testJson),
                "test.json was not written — listener wiring may be broken");
        String content = Files.readString(testJson);
        // @TestFactory tests are not known at discovery; the listener grows expectedCount
        // via dynamicTestRegistered so a completed dynamic run reports "passed".
        assertTrue(content.contains("\"reason\": \"passed\""),
                "a completed @TestFactory run should report passed, not interrupted");
    }

    @Test
    void emitsPassedReasonForAbortedContainer(@TempDir Path tmp) throws Exception {
        TddGuardListener listener = createListener(tmp);

        var request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(AbortedContainerFixture.class))
                .build();
        var launcher = LauncherFactory.create();
        launcher.execute(request, listener);

        Path testJson = tmp.resolve(DATA_PATH);
        assertTrue(Files.exists(testJson),
                "test.json was not written — listener wiring may be broken");
        String content = Files.readString(testJson);
        // When @BeforeAll aborts (Assumptions.assumeTrue(false)), the platform fires an ABORTED
        // result on the container and no child events. The count becomes untrustworthy so the
        // listener must report "passed", not "interrupted".
        assertTrue(content.contains("\"reason\": \"passed\""),
                "an aborted container should report passed, not interrupted");
    }

    private static TddGuardListener createListener(Path projectRoot) {
        ProjectRootResolver resolver = new ProjectRootResolver(
                name -> projectRoot.toString(),
                () -> projectRoot);
        return new TddGuardListener(resolver, new TestJsonWriter(), name -> projectRoot.toString());
    }
}
