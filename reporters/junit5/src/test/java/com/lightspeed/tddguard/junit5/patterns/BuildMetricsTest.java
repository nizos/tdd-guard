package com.lightspeed.tddguard.junit5.patterns;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for BuildMetrics.
 * Validates build metrics collection and defaults.
 */
class BuildMetricsTest {

    @Test
    void shouldCreateWithDefaults() {
        // When
        BuildMetrics metrics = BuildMetrics.empty();

        // Then
        assertEquals(0L, metrics.getTestCompilationTime());
        assertFalse(metrics.isIncrementalCompilationEnabled());
    }

    @Test
    void shouldCreateWithExplicitValues() {
        // When
        BuildMetrics metrics = new BuildMetrics(3000L, true);

        // Then
        assertEquals(3000L, metrics.getTestCompilationTime());
        assertTrue(metrics.isIncrementalCompilationEnabled());
    }

    @Test
    void shouldCollectFromGradleBuildScan(@TempDir Path projectRoot) throws IOException {
        // Given
        Path buildDir = projectRoot.resolve("build");
        Files.createDirectories(buildDir);

        // Simulate Gradle build scan data
        String buildScanData = "Task :compileTestJava took 2500ms\n" +
            "Incremental compilation: enabled\n";
        Files.writeString(buildDir.resolve("build-scan.txt"), buildScanData);

        // When
        BuildMetrics metrics = BuildMetrics.collect(projectRoot);

        // Then - Should return defaults when build scan parsing not implemented yet
        assertEquals(0L, metrics.getTestCompilationTime());
    }

    @Test
    void shouldHandleMissingBuildDirectory(@TempDir Path projectRoot) {
        // When
        BuildMetrics metrics = BuildMetrics.collect(projectRoot);

        // Then - Should return defaults gracefully
        assertNotNull(metrics);
        assertEquals(0L, metrics.getTestCompilationTime());
    }

    @Test
    void shouldRejectNegativeCompilationTime() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            new BuildMetrics(-1L, false)
        );
    }

    @Test
    void shouldCreateWithBuildTimeHistory() {
        // Given
        java.util.List<Long> history = java.util.List.of(1000L, 1200L, 1100L);

        // When
        BuildMetrics metrics = BuildMetrics.withHistory(history);

        // Then
        assertEquals(history, metrics.getBuildTimeHistory());
        assertEquals(3, metrics.getBuildTimeHistory().size());
    }

    @Test
    void shouldReturnEmptyHistoryForDefaultMetrics() {
        // When
        BuildMetrics metrics = BuildMetrics.empty();

        // Then
        assertNotNull(metrics.getBuildTimeHistory());
        assertTrue(metrics.getBuildTimeHistory().isEmpty());
    }

    @Test
    void shouldRejectNullHistory() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
            BuildMetrics.withHistory(null)
        );
    }
}
