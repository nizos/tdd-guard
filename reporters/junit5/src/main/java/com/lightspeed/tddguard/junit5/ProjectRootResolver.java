package com.lightspeed.tddguard.junit5;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Resolves the project root directory using a fallback hierarchy:
 * 1. System property: tddguard.projectRoot
 * 2. Environment variable: TDDGUARD_PROJECT_ROOT
 * 3. Traverse up from starting directory looking for build.gradle, build.gradle.kts, or pom.xml
 * 4. Fallback to starting directory (working directory)
 *
 * This class is designed for testability with dependency injection of suppliers.
 */
class ProjectRootResolver {

    private final Supplier<String> sysPropSupplier;
    private final Supplier<String> envVarSupplier;
    private final Path startingDirectory;

    /**
     * Constructor for testing with dependency injection.
     *
     * @param sysPropSupplier     Supplier for tddguard.projectRoot system property
     * @param envVarSupplier      Supplier for TDDGUARD_PROJECT_ROOT environment variable
     * @param startingDirectory   Starting directory for traversal
     */
    ProjectRootResolver(Supplier<String> sysPropSupplier, Supplier<String> envVarSupplier, Path startingDirectory) {
        this.sysPropSupplier = sysPropSupplier;
        this.envVarSupplier = envVarSupplier;
        this.startingDirectory = startingDirectory;
    }

    /**
     * Resolves the project root directory using the fallback hierarchy.
     *
     * @return The resolved project root path (always absolute)
     */
    Path resolve() {
        // 1. System property
        String sysProp = sysPropSupplier.get();
        if (sysProp != null) {
            return Path.of(sysProp).toAbsolutePath();
        }

        // 2. Environment variable
        String envVar = envVarSupplier.get();
        if (envVar != null) {
            return Path.of(envVar).toAbsolutePath();
        }

        // 3. Traverse up looking for build files
        Path current = startingDirectory.toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("build.gradle")) ||
                Files.exists(current.resolve("build.gradle.kts")) ||
                Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }

        // 4. Fallback to starting directory
        Path fallbackPath = startingDirectory.toAbsolutePath();
        System.err.println(
            "TDD Guard: No build file found (build.gradle, build.gradle.kts, or pom.xml). " +
            "Falling back to working directory: " + fallbackPath
        );
        return fallbackPath;
    }
}
