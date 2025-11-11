package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects opportunities for test-fixtures pattern.
 * Measures compilation time and dependency complexity.
 * All feedback is fact-based with measurable metrics.
 */
public class TestFixturesOpportunityDetector implements PatternDetector {

    private static final long COMPILATION_TIME_THRESHOLD_MS = 2000L;
    private static final double DEPENDENCY_DEPTH_THRESHOLD = 3.0;

    // Pattern to find constructor calls and count parameters
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile("new\\s+[A-Z][A-Za-z0-9_]*\\s*\\([^)]*\\)");

    private final SourceAnalyzer sourceAnalyzer;

    public TestFixturesOpportunityDetector(SourceAnalyzer sourceAnalyzer) {
        this.sourceAnalyzer = sourceAnalyzer;
    }

    @Override
    public String getCategory() {
        return "test-fixtures-opportunity";
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

        long compilationMs = buildMetrics.getTestCompilationTime();

        // Analyze dependency depth
        List<Integer> depths = new ArrayList<>();

        for (Path testFile : testFiles) {
            try {
                String content = Files.readString(testFile);
                int maxDepth = analyzeConstructorComplexity(content);
                depths.add(maxDepth);
            } catch (IOException e) {
                // Skip files that can't be read
                continue;
            }
        }

        double avgDepth = depths.stream()
            .mapToInt(Integer::intValue)
            .average()
            .orElse(0.0);

        // Trigger threshold: compilation > 2000ms OR avg depth > 3
        if (compilationMs <= COMPILATION_TIME_THRESHOLD_MS && avgDepth <= DEPENDENCY_DEPTH_THRESHOLD) {
            return Optional.empty();
        }

        // Build educational feedback
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("compilationMs", compilationMs);
        evidence.put("depthAvg", String.format("%.1f", avgDepth));
        evidence.put("patternCount", 0); // Simplified: not detecting repeated patterns yet

        String message = buildMessage(compilationMs, avgDepth);
        String recommendation = buildRecommendation();

        return Optional.of(new EducationalFeedback(
            "test-fixtures-opportunity",
            "info",
            "Test-fixtures opportunity detected",
            evidence,
            message,
            recommendation
        ));
    }

    private int analyzeConstructorComplexity(String content) {
        Matcher matcher = CONSTRUCTOR_PATTERN.matcher(content);

        int maxParams = 0;
        while (matcher.find()) {
            String constructorCall = matcher.group();
            String params = extractParameters(constructorCall);

            if (!params.isEmpty()) {
                int paramCount = countParameters(params);
                maxParams = Math.max(maxParams, paramCount);
            }
        }

        return maxParams;
    }

    private String extractParameters(String constructorCall) {
        int openParen = constructorCall.indexOf('(');
        int closeParen = constructorCall.lastIndexOf(')');

        if (openParen < 0 || closeParen < 0) {
            return "";
        }

        return constructorCall.substring(openParen + 1, closeParen).trim();
    }

    private int countParameters(String params) {
        if (params.isEmpty()) {
            return 0;
        }

        // Simple count: split by comma, accounting for nested parentheses
        int count = 1;
        int depth = 0;

        for (char c : params.toCharArray()) {
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
            } else if (c == ',' && depth == 0) {
                count++;
            }
        }

        return count;
    }

    private String buildMessage(long compilationMs, double avgDepth) {
        return String.format(
            "Test compilation took %dms with average dependency depth of %.1f. " +
            "Test-fixtures pre-instantiate dependency graphs, reducing compilation overhead.",
            compilationMs,
            avgDepth
        );
    }

    private String buildRecommendation() {
        return "Consider implementing test-fixtures to pre-build complex dependency graphs. " +
               "See Villenele Framework pattern for reusable test infrastructure.";
    }
}
