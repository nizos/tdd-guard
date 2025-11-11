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

/**
 * Detects missing test isolation issues.
 * Identifies hardcoded URLs, file paths, and ports.
 * All feedback is fact-based with exact counts and examples.
 */
public class MissingIsolationDetector implements PatternDetector {

    // Pattern for HTTP(S) URLs
    private static final Pattern URL_PATTERN = Pattern.compile(
        "(https?://[a-zA-Z0-9.-]+(?::[0-9]+)?(?:/[^\\s\"')*]*)?)"
    );

    // Pattern for absolute file paths (Unix and Windows)
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "([\"'](/[a-zA-Z0-9_./]+|[A-Z]:\\\\[a-zA-Z0-9_\\\\]+)[\"'])"
    );

    // Pattern for port numbers (4-5 digits)
    private static final Pattern PORT_PATTERN = Pattern.compile(
        "\\b([0-9]{4,5})\\b"
    );

    private final SourceAnalyzer sourceAnalyzer;

    public MissingIsolationDetector(SourceAnalyzer sourceAnalyzer) {
        this.sourceAnalyzer = sourceAnalyzer;
    }

    @Override
    public String getCategory() {
        return "missing-isolation";
    }

    @Override
    public Optional<EducationalFeedback> detect(
        TestJson testResults,
        Path projectRoot,
        BuildMetrics buildMetrics
    ) {
        List<Path> testFiles = sourceAnalyzer.findTestFiles(projectRoot);

        if (testFiles.isEmpty()) {
            return Optional.empty();
        }

        List<HardcodedResource> hardcoded = new ArrayList<>();

        for (Path testFile : testFiles) {
            try {
                String content = Files.readString(testFile);
                String relativePath = projectRoot.relativize(testFile).toString();

                // Detect hardcoded URLs
                Matcher urlMatcher = URL_PATTERN.matcher(content);
                while (urlMatcher.find()) {
                    hardcoded.add(new HardcodedResource(
                        "URL",
                        urlMatcher.group(1),
                        relativePath
                    ));
                }

                // Detect hardcoded file paths (excluding test resource paths)
                Matcher pathMatcher = FILE_PATH_PATTERN.matcher(content);
                while (pathMatcher.find()) {
                    String path = pathMatcher.group(2);
                    if (!path.contains("test") && !path.contains("resources")) {
                        hardcoded.add(new HardcodedResource(
                            "FilePath",
                            path,
                            relativePath
                        ));
                    }
                }

                // Detect hardcoded ports
                Matcher portMatcher = PORT_PATTERN.matcher(content);
                while (portMatcher.find()) {
                    int port = Integer.parseInt(portMatcher.group(1));
                    // Valid port range, excluding common year patterns
                    if (port >= 1024 && port <= 65535 && port < 2100) {
                        hardcoded.add(new HardcodedResource(
                            "Port",
                            String.valueOf(port),
                            relativePath
                        ));
                    }
                }
            } catch (IOException e) {
                // Skip files that can't be read
                continue;
            }
        }

        // Trigger threshold: any hardcoded resources found
        if (hardcoded.isEmpty()) {
            return Optional.empty();
        }

        // Build educational feedback
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("hardcodedCount", hardcoded.size());
        evidence.put("paramCount", "0.0"); // Simplified: not analyzing constructor params
        evidence.put("accessPoints", 0);  // Simplified: not detecting external access yet
        evidence.put("examples", hardcoded.stream()
            .limit(5)
            .map(h -> h.type + ": " + h.value + " in " + h.file)
            .collect(Collectors.toList()));

        String message = buildMessage(hardcoded.size());
        String recommendation = buildRecommendation();

        return Optional.of(new EducationalFeedback(
            "missing-isolation",
            "warning",
            "Tests depend on external resources",
            evidence,
            message,
            recommendation
        ));
    }

    private String buildMessage(int hardcodedCount) {
        return String.format(
            "Found %d hardcoded URLs/paths in test code. " +
            "Consider dependency injection with test doubles for external resources.",
            hardcodedCount
        );
    }

    private String buildRecommendation() {
        return "Use dependency injection to inject test doubles instead of hardcoding external resources. " +
               "This improves test isolation, speed, and reliability.";
    }

    /**
     * Represents a hardcoded resource found in test code.
     */
    private static class HardcodedResource {
        final String type;
        final String value;
        final String file;

        HardcodedResource(String type, String value, String file) {
            this.type = type;
            this.value = value;
            this.file = file;
        }
    }
}
