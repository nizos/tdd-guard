package com.lightspeed.tddguard.junit5;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves source directory paths for test and main code.
 */
public class SourceDirectoryResolver {

    private final Path projectRoot;
    private final Function<String, String> sysPropSupplier;
    private final Function<String, String> envVarSupplier;

    public SourceDirectoryResolver(
        Path projectRoot,
        Function<String, String> sysPropSupplier,
        Function<String, String> envVarSupplier
    ) {
        this.projectRoot = projectRoot;
        this.sysPropSupplier = sysPropSupplier;
        this.envVarSupplier = envVarSupplier;
    }

    public List<String> resolveTestSourceDirs() {
        return resolveDirectories("tddguard.testSourceDirs", "TDDGUARD_TEST_SOURCE_DIRS");
    }

    public List<String> resolveMainSourceDirs() {
        return resolveDirectories("tddguard.mainSourceDirs", "TDDGUARD_MAIN_SOURCE_DIRS");
    }

    private List<String> resolveDirectories(String sysPropName, String envVarName) {
        // Try system property first
        String dirs = sysPropSupplier.apply(sysPropName);

        // Fall back to environment variable if system property is null or empty
        if (dirs == null || dirs.trim().isEmpty()) {
            dirs = envVarSupplier.apply(envVarName);
        }

        // Return empty list if both sources are null or empty
        if (dirs == null || dirs.trim().isEmpty()) {
            return java.util.Collections.emptyList();
        }

        // Parse comma-separated values, trim, and filter out empty strings
        return Arrays.stream(dirs.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    }
}
