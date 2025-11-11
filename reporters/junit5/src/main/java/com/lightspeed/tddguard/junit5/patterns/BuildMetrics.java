package com.lightspeed.tddguard.junit5.patterns;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Build and compilation metrics for pattern detection.
 * Provides measurable data about build performance.
 */
public class BuildMetrics {

    private final long testCompilationTime;
    private final boolean incrementalCompilationEnabled;
    private final List<Long> buildTimeHistory;

    /**
     * Creates build metrics with explicit values.
     *
     * @param testCompilationTime           Test compilation time in milliseconds
     * @param incrementalCompilationEnabled Whether incremental compilation is enabled
     * @throws IllegalArgumentException if compilationTime is negative
     */
    public BuildMetrics(long testCompilationTime, boolean incrementalCompilationEnabled) {
        this(testCompilationTime, incrementalCompilationEnabled, Collections.emptyList());
    }

    /**
     * Creates build metrics with explicit values including build time history.
     *
     * @param testCompilationTime           Test compilation time in milliseconds
     * @param incrementalCompilationEnabled Whether incremental compilation is enabled
     * @param buildTimeHistory              List of recent build times in milliseconds
     * @throws IllegalArgumentException if compilationTime is negative or history is null
     */
    public BuildMetrics(long testCompilationTime, boolean incrementalCompilationEnabled, List<Long> buildTimeHistory) {
        if (testCompilationTime < 0) {
            throw new IllegalArgumentException("Compilation time cannot be negative");
        }
        if (buildTimeHistory == null) {
            throw new IllegalArgumentException("Build time history cannot be null");
        }
        this.testCompilationTime = testCompilationTime;
        this.incrementalCompilationEnabled = incrementalCompilationEnabled;
        this.buildTimeHistory = new ArrayList<>(buildTimeHistory);
    }

    /**
     * Returns empty metrics with default values.
     *
     * @return Empty build metrics
     */
    public static BuildMetrics empty() {
        return new BuildMetrics(0L, false);
    }

    /**
     * Collects build metrics from project.
     * Currently returns defaults - future enhancement will parse build output.
     *
     * @param projectRoot Project root directory
     * @return Build metrics (defaults for now)
     */
    public static BuildMetrics collect(Path projectRoot) {
        // Future enhancement: Parse Gradle build scan or Maven surefire reports
        // For now, return defaults
        return empty();
    }

    /**
     * Returns test compilation time in milliseconds.
     *
     * @return Compilation time in ms
     */
    public long getTestCompilationTime() {
        return testCompilationTime;
    }

    /**
     * Returns whether incremental compilation is enabled.
     *
     * @return true if incremental compilation enabled
     */
    public boolean isIncrementalCompilationEnabled() {
        return incrementalCompilationEnabled;
    }

    /**
     * Returns the build time history.
     *
     * @return Unmodifiable list of build times in milliseconds
     */
    public List<Long> getBuildTimeHistory() {
        return Collections.unmodifiableList(buildTimeHistory);
    }

    /**
     * Creates build metrics with only build time history.
     *
     * @param buildTimeHistory List of recent build times in milliseconds
     * @return BuildMetrics with history
     * @throws IllegalArgumentException if history is null
     */
    public static BuildMetrics withHistory(List<Long> buildTimeHistory) {
        if (buildTimeHistory == null) {
            throw new IllegalArgumentException("Build time history cannot be null");
        }
        return new BuildMetrics(0L, false, buildTimeHistory);
    }
}
