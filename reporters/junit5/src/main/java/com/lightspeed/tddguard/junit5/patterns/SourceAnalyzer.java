package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.SourceDirectoryResolver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Analyzes source code structure.
 * Finds test files using configured test directories.
 */
public class SourceAnalyzer {

    private static final List<String> DEFAULT_TEST_DIRS = Arrays.asList(
        "src/test/java",
        "src/test/kotlin",
        "src/test"
    );

    private final SourceDirectoryResolver directoryResolver;

    public SourceAnalyzer(SourceDirectoryResolver directoryResolver) {
        this.directoryResolver = directoryResolver;
    }

    /**
     * Finds all test source files in the project.
     * Uses configured test directories or defaults if not configured.
     * Includes Java and Kotlin test files.
     * Excludes build output directories.
     *
     * @param projectRoot Project root directory
     * @return List of test file paths
     */
    public List<Path> findTestFiles(Path projectRoot) {
        List<String> testDirs = directoryResolver.resolveTestSourceDirs();

        // Use defaults if no directories configured
        if (testDirs.isEmpty()) {
            testDirs = DEFAULT_TEST_DIRS;
        }

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            final List<String> effectiveTestDirs = testDirs;
            return paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                .filter(p -> isInTestDirectory(p, effectiveTestDirs))
                .filter(p -> !p.toString().contains("/build/"))
                .filter(p -> !p.toString().contains("/target/"))
                .collect(Collectors.toList());
        } catch (IOException e) {
            // Return empty list if walk fails
            return Collections.emptyList();
        }
    }

    /**
     * Checks if a path is within any of the configured test directories.
     *
     * @param path Path to check
     * @param testDirs List of test directory patterns
     * @return true if path is in a test directory
     */
    private boolean isInTestDirectory(Path path, List<String> testDirs) {
        String pathStr = path.toString();
        return testDirs.stream()
            .anyMatch(dir -> pathStr.contains("/" + dir + "/") || pathStr.contains("/" + dir));
    }
}
