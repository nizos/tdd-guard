package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Detects file structure inconsistencies in test code.
 * Analyzes test file organization, naming conventions, and package structure.
 * All feedback is fact-based with measurable evidence.
 */
public class FileStructureAnalyzer implements PatternDetector {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\s+([a-z][a-z0-9_.]*);", Pattern.MULTILINE);
    private static final Pattern CLASS_NAME_PATTERN = Pattern.compile("\\bclass\\s+(\\w+)");

    private final SourceAnalyzer sourceAnalyzer;

    public FileStructureAnalyzer(SourceAnalyzer sourceAnalyzer) {
        this.sourceAnalyzer = sourceAnalyzer;
    }

    @Override
    public String getCategory() {
        return "file-structure";
    }

    @Override
    public Optional<EducationalFeedback> detect(
        TestJson testResults,
        Path projectRoot,
        BuildMetrics buildMetrics
    ) {
        // Early return for empty projects
        if (testResults.summary.total == 0) {
            return Optional.empty();
        }

        List<String> testsInMain = findTestsInProductionDirectory(projectRoot);
        List<Map<String, String>> packageMismatches = findPackageMismatches(projectRoot);
        List<String> namingViolations = findNamingViolations(projectRoot);

        // Only report if we found issues
        if (testsInMain.isEmpty() && packageMismatches.isEmpty() && namingViolations.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(createFeedback(testsInMain, packageMismatches, namingViolations));
    }

    /**
     * Finds test files in production source directories (src/main/java or src/main/kotlin).
     * This is a critical error as tests should never be in production code.
     */
    private List<String> findTestsInProductionDirectory(Path projectRoot) {
        List<String> testsInMain = new ArrayList<>();

        try (Stream<Path> paths = Files.walk(projectRoot)) {
            paths.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                .filter(p -> p.toString().contains("/src/main/"))
                .filter(p -> !p.toString().contains("/build/"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(this::looksLikeTestFile)
                .forEach(path -> testsInMain.add(projectRoot.relativize(path).toString()));
        } catch (IOException e) {
            // Return what we've collected so far
        }

        return testsInMain;
    }

    /**
     * Finds package structure mismatches between test files and their corresponding production code.
     */
    private List<Map<String, String>> findPackageMismatches(Path projectRoot) {
        List<Map<String, String>> mismatches = new ArrayList<>();

        List<Path> testFiles = findTestFilesInTestDirectory(projectRoot);

        for (Path testFile : testFiles) {
            try {
                String content = Files.readString(testFile);
                String testPackage = extractPackage(content);
                String className = extractClassName(testFile.getFileName().toString());

                if (testPackage != null && className != null) {
                    // Try to find corresponding production class
                    String expectedProductionClass = className.replace("Test", "")
                        .replaceFirst("^Test", "");

                    if (!expectedProductionClass.equals(className)) {
                        Optional<String> productionPackage = findProductionPackage(
                            projectRoot, expectedProductionClass
                        );

                        if (productionPackage.isPresent() && !productionPackage.get().equals(testPackage)) {
                            Map<String, String> mismatch = new HashMap<>();
                            mismatch.put("testClass", className);
                            mismatch.put("testPackage", testPackage);
                            mismatch.put("expectedPackage", productionPackage.get());
                            mismatches.add(mismatch);
                        }
                    }
                }
            } catch (IOException e) {
                // Skip this file
            }
        }

        return mismatches;
    }

    /**
     * Finds test files with naming convention violations (missing Test suffix/prefix).
     */
    private List<String> findNamingViolations(Path projectRoot) {
        List<String> violations = new ArrayList<>();

        List<Path> testFiles = findTestFilesInTestDirectory(projectRoot);

        for (Path testFile : testFiles) {
            try {
                String content = Files.readString(testFile);
                String className = extractClassName(testFile.getFileName().toString());

                if (className != null && containsTestAnnotations(content)) {
                    if (!className.endsWith("Test") && !className.startsWith("Test")) {
                        violations.add(String.format(
                            "%s: Class '%s' contains test methods but lacks Test suffix or Test prefix",
                            projectRoot.relativize(testFile),
                            className
                        ));
                    }
                }
            } catch (IOException e) {
                // Skip this file
            }
        }

        return violations;
    }

    /**
     * Finds all test files in test directories.
     */
    private List<Path> findTestFilesInTestDirectory(Path projectRoot) {
        return sourceAnalyzer.findTestFiles(projectRoot);
    }

    /**
     * Checks if a file looks like a test file based on its name.
     */
    private boolean looksLikeTestFile(Path path) {
        String filename = path.getFileName().toString();
        return filename.contains("Test") || filename.contains("Spec");
    }

    /**
     * Checks if content contains JUnit test annotations.
     */
    private boolean containsTestAnnotations(String content) {
        return content.contains("@Test") ||
               content.contains("@org.junit.Test") ||
               content.contains("@org.junit.jupiter.api.Test");
    }

    /**
     * Extracts package declaration from source code.
     */
    private String extractPackage(String content) {
        Matcher matcher = PACKAGE_PATTERN.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts class name from filename.
     */
    private String extractClassName(String filename) {
        if (filename.endsWith(".java")) {
            return filename.substring(0, filename.length() - 5);
        } else if (filename.endsWith(".kt")) {
            return filename.substring(0, filename.length() - 3);
        }
        return null;
    }

    /**
     * Finds the package of a production class.
     */
    private Optional<String> findProductionPackage(Path projectRoot, String className) {
        try (Stream<Path> paths = Files.walk(projectRoot)) {
            List<Path> matches = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".kt"))
                .filter(p -> p.toString().contains("/src/main/"))
                .filter(p -> !p.toString().contains("/build/"))
                .filter(p -> !p.toString().contains("/target/"))
                .filter(p -> {
                    String filename = p.getFileName().toString();
                    String fileClassName = extractClassName(filename);
                    return className.equals(fileClassName);
                })
                .collect(Collectors.toList());

            if (!matches.isEmpty()) {
                String content = Files.readString(matches.get(0));
                return Optional.ofNullable(extractPackage(content));
            }
        } catch (IOException e) {
            // Return empty
        }

        return Optional.empty();
    }

    /**
     * Creates educational feedback from detected issues.
     */
    private EducationalFeedback createFeedback(
        List<String> testsInMain,
        List<Map<String, String>> packageMismatches,
        List<String> namingViolations
    ) {
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("testsInMain", testsInMain);
        evidence.put("packageMismatches", packageMismatches);
        evidence.put("namingViolations", namingViolations);

        String title = determineTitle(testsInMain, packageMismatches, namingViolations);
        String message = buildMessage(testsInMain, packageMismatches, namingViolations);
        String recommendation = buildRecommendation(testsInMain, packageMismatches, namingViolations);

        return new EducationalFeedback(
            "file-structure",
            "warning",
            title,
            evidence,
            message,
            recommendation
        );
    }

    private String determineTitle(
        List<String> testsInMain,
        List<Map<String, String>> packageMismatches,
        List<String> namingViolations
    ) {
        if (!testsInMain.isEmpty()) {
            return "Tests Found in Production Code Directory";
        } else if (!packageMismatches.isEmpty()) {
            return "Package Structure Inconsistencies Detected";
        } else {
            return "Test Naming Convention Violations Detected";
        }
    }

    private String buildMessage(
        List<String> testsInMain,
        List<Map<String, String>> packageMismatches,
        List<String> namingViolations
    ) {
        StringBuilder message = new StringBuilder();
        message.append("File structure analysis detected the following issues:\n\n");

        if (!testsInMain.isEmpty()) {
            message.append(String.format("CRITICAL: Found %d test file(s) in src/main/java directory:\n",
                testsInMain.size()));
            testsInMain.forEach(file -> message.append("  - ").append(file).append("\n"));
            message.append("\n");
        }

        if (!packageMismatches.isEmpty()) {
            message.append(String.format("Found %d package structure mismatch(es):\n",
                packageMismatches.size()));
            packageMismatches.forEach(mismatch ->
                message.append(String.format("  - %s: package mismatch - test: '%s', production: '%s'\n",
                    mismatch.get("testClass"),
                    mismatch.get("testPackage"),
                    mismatch.get("expectedPackage")
                ))
            );
            message.append("\n");
        }

        if (!namingViolations.isEmpty()) {
            message.append(String.format("Found %d naming convention violation(s):\n",
                namingViolations.size()));
            namingViolations.forEach(violation ->
                message.append("  - ").append(violation).append("\n")
            );
        }

        return message.toString().trim();
    }

    private String buildRecommendation(
        List<String> testsInMain,
        List<Map<String, String>> packageMismatches,
        List<String> namingViolations
    ) {
        StringBuilder recommendation = new StringBuilder();

        if (!testsInMain.isEmpty()) {
            recommendation.append("Move test files from src/main/java to src/test/java. ");
            recommendation.append("Tests are excluded from production builds when placed in src/test/java. ");
        }

        if (!packageMismatches.isEmpty()) {
            recommendation.append("Align test package structure with production code. ");
            recommendation.append("Tests mirror the package structure of the code they test, maintaining consistent navigation. ");
        }

        if (!namingViolations.isEmpty()) {
            recommendation.append("Follow naming conventions: test classes end with 'Test' or start with 'Test'. ");
            recommendation.append("This naming pattern makes test files easily identifiable and allows build tools to recognize them. ");
        }

        return recommendation.toString().trim();
    }
}
