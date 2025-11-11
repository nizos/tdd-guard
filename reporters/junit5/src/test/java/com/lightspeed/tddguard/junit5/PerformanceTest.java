package com.lightspeed.tddguard.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance tests verifying TddGuardListener overhead requirements.
 *
 * Covers: AC#2 - Performance Requirements
 * - When disabled: <100µs overhead per test
 * - When enabled: <1ms overhead per test
 */
class PerformanceTest {

    private static final int WARMUP_ITERATIONS = 100;
    private static final int TEST_ITERATIONS = 1000;
    private static final long DISABLED_THRESHOLD_NS = 100_000; // 100µs in nanoseconds
    private static final long ENABLED_THRESHOLD_NS = 1_000_000; // 1ms in nanoseconds

    @Test
    void shouldHaveMinimalOverheadWhenDisabled(@TempDir Path tempDir) {
        // Given: Listener is disabled
        TddGuardListener listener = new TddGuardListener(
            tempDir,
            () -> null, // No env var
            () -> null  // No system property
        );
        assertFalse(listener.isEnabled(), "Listener should be disabled");

        TestIdentifier testId = createTestIdentifier();
        TestExecutionResult result = TestExecutionResult.successful();

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            listener.executionStarted(testId);
            listener.executionFinished(testId, result);
        }

        // When: Measuring overhead of disabled listener
        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            listener.executionStarted(testId);
            listener.executionFinished(testId, result);
        }
        long endTime = System.nanoTime();

        // Then: Average overhead should be <100µs per test
        long totalTime = endTime - startTime;
        long avgTimePerTest = totalTime / TEST_ITERATIONS;

        assertTrue(
            avgTimePerTest < DISABLED_THRESHOLD_NS,
            String.format(
                "Disabled listener overhead should be <100µs per test, but was %dµs (%.2fµs avg)",
                avgTimePerTest / 1000,
                avgTimePerTest / 1000.0
            )
        );

        System.out.printf(
            "Performance (disabled): %dµs avg per test (threshold: 100µs)%n",
            avgTimePerTest / 1000
        );
    }

    @Test
    void shouldHaveAcceptableOverheadWhenEnabled(@TempDir Path tempDir) throws Exception {
        // Given: Listener is enabled
        Path tddGuardDir = tempDir.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        TddGuardListener listener = new TddGuardListener(
            tempDir,
            () -> null, // Directory exists, so env var not needed
            () -> null
        );
        assertTrue(listener.isEnabled(), "Listener should be enabled");

        TestIdentifier testId = createTestIdentifier();
        TestExecutionResult result = TestExecutionResult.successful();

        // Warmup (need to initialize collector first)
        listener.testPlanExecutionStarted(null);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            listener.executionStarted(testId);
            listener.executionFinished(testId, result);
        }

        // When: Measuring overhead of enabled listener
        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            listener.executionStarted(testId);
            listener.executionFinished(testId, result);
        }
        long endTime = System.nanoTime();

        // Then: Average overhead should be <1ms per test
        long totalTime = endTime - startTime;
        long avgTimePerTest = totalTime / TEST_ITERATIONS;

        assertTrue(
            avgTimePerTest < ENABLED_THRESHOLD_NS,
            String.format(
                "Enabled listener overhead should be <1ms per test, but was %dµs (%.2fµs avg)",
                avgTimePerTest / 1000,
                avgTimePerTest / 1000.0
            )
        );

        System.out.printf(
            "Performance (enabled): %dµs avg per test (threshold: 1000µs)%n",
            avgTimePerTest / 1000
        );
    }

    @Test
    void shouldShowPerformanceDifferenceBetweenEnabledAndDisabled(@TempDir Path tempDir) throws Exception {
        // Given: Both enabled and disabled listeners
        TddGuardListener disabledListener = new TddGuardListener(
            tempDir,
            () -> null,
            () -> null
        );

        Path tddGuardDir = tempDir.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);
        TddGuardListener enabledListener = new TddGuardListener(
            tempDir,
            () -> null,
            () -> null
        );

        TestIdentifier testId = createTestIdentifier();
        TestExecutionResult result = TestExecutionResult.successful();

        // When: Measuring both
        long disabledTime = measureOverhead(disabledListener, testId, result, false);
        long enabledTime = measureOverhead(enabledListener, testId, result, true);

        // Then: Enabled overhead should be measurably higher but still acceptable
        assertTrue(
            enabledTime > disabledTime,
            String.format(
                "Enabled overhead (%dµs) should be higher than disabled (%dµs)",
                enabledTime / 1000,
                disabledTime / 1000
            )
        );

        System.out.printf(
            "Performance comparison: disabled=%dµs, enabled=%dµs (%.1fx overhead)%n",
            disabledTime / 1000,
            enabledTime / 1000,
            (double) enabledTime / disabledTime
        );
    }

    private long measureOverhead(
        TddGuardListener listener,
        TestIdentifier testId,
        TestExecutionResult result,
        boolean needsInit
    ) {
        // Initialize collector if enabled
        if (needsInit) {
            listener.testPlanExecutionStarted(null);
        }

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            listener.executionStarted(testId);
            listener.executionFinished(testId, result);
        }

        // Measure
        long startTime = System.nanoTime();
        for (int i = 0; i < TEST_ITERATIONS; i++) {
            listener.executionStarted(testId);
            listener.executionFinished(testId, result);
        }
        long endTime = System.nanoTime();

        return (endTime - startTime) / TEST_ITERATIONS;
    }

    private TestIdentifier createTestIdentifier() {
        MethodSource source = MethodSource.from("PerformanceTest", "testMethod");
        return TestIdentifier.from(new TestDescriptorStub(
            "test",
            "testMethod",
            source
        ));
    }
}
