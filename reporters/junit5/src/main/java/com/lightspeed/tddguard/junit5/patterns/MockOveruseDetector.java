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
 * Detects mock overuse anti-pattern in test code.
 * Measures @Mock annotation usage and identifies value object mocks.
 * All feedback is fact-based with exact counts and ratios.
 */
public class MockOveruseDetector implements PatternDetector {

    private static final double MOCK_RATIO_THRESHOLD = 2.0;
    private static final Pattern MOCK_ANNOTATION = Pattern.compile("@Mock\\b");
    private static final Pattern MOCKBEAN_ANNOTATION = Pattern.compile("@MockBean\\b");
    private static final Pattern MOCKITO_MOCK_CALL = Pattern.compile("\\bmock\\(");

    // Pattern to extract mocked type names
    private static final Pattern MOCK_FIELD_PATTERN =
        Pattern.compile("@Mock(?:Bean)?\\s+(?:private\\s+)?([A-Z][A-Za-z0-9_]*)\\s+");

    // Pattern to extract Mockito.mock() type names
    private static final Pattern MOCKITO_PATTERN =
        Pattern.compile("mock\\(([A-Z][A-Za-z0-9_]*)\\.class");

    private final SourceAnalyzer sourceAnalyzer;

    public MockOveruseDetector(SourceAnalyzer sourceAnalyzer) {
        this.sourceAnalyzer = sourceAnalyzer;
    }

    @Override
    public String getCategory() {
        return "mock-overuse";
    }

    @Override
    public Optional<EducationalFeedback> detect(
        TestJson testResults,
        Path projectRoot,
        BuildMetrics buildMetrics
    ) {
        List<Path> testFiles = sourceAnalyzer.findTestFiles(projectRoot);

        int mockCount = 0;
        Set<String> valueObjectMocks = new LinkedHashSet<>();
        String exampleFile = null;

        for (Path testFile : testFiles) {
            try {
                String content = Files.readString(testFile);

                // Count all mock types
                mockCount += countMatches(MOCK_ANNOTATION, content);
                mockCount += countMatches(MOCKBEAN_ANNOTATION, content);
                mockCount += countMatches(MOCKITO_MOCK_CALL, content);

                // Extract and check for value object mocks
                List<String> mockedTypes = extractMockedTypes(content);
                for (String type : mockedTypes) {
                    if (isLikelyValueObject(type)) {
                        valueObjectMocks.add(type);
                        if (exampleFile == null) {
                            exampleFile = projectRoot.relativize(testFile).toString();
                        }
                    }
                }
            } catch (IOException e) {
                // Skip files that can't be read
                continue;
            }
        }

        int testCount = testResults.summary.total;

        // Avoid division by zero
        if (testCount == 0) {
            return Optional.empty();
        }

        double ratio = (double) mockCount / testCount;

        // Trigger threshold: ratio > 2.0 OR any value object mocks
        if (ratio <= MOCK_RATIO_THRESHOLD && valueObjectMocks.isEmpty()) {
            return Optional.empty();
        }

        // Build educational feedback
        Map<String, Object> evidence = new HashMap<>();
        evidence.put("mockCount", mockCount);
        evidence.put("testCount", testCount);
        evidence.put("ratio", String.format("%.2f", ratio));
        evidence.put("valueObjectMocks", new ArrayList<>(valueObjectMocks));
        evidence.put("exampleFile", exampleFile);

        String message = buildMessage(mockCount, testCount, ratio, valueObjectMocks);
        String recommendation = buildRecommendation();

        return Optional.of(new EducationalFeedback(
            "mock-overuse",
            "warning",
            "High mock usage detected",
            evidence,
            message,
            recommendation
        ));
    }

    private int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private List<String> extractMockedTypes(String content) {
        List<String> types = new ArrayList<>();

        // Extract from @Mock and @MockBean field declarations
        Matcher fieldMatcher = MOCK_FIELD_PATTERN.matcher(content);
        while (fieldMatcher.find()) {
            types.add(fieldMatcher.group(1));
        }

        // Extract from Mockito.mock() calls
        Matcher mockitoMatcher = MOCKITO_PATTERN.matcher(content);
        while (mockitoMatcher.find()) {
            types.add(mockitoMatcher.group(1));
        }

        return types;
    }

    private boolean isLikelyValueObject(String typeName) {
        // Heuristic: Common value object name patterns
        return typeName.endsWith("Id") ||
               typeName.endsWith("Value") ||
               typeName.equals("Money") ||
               typeName.equals("Amount") ||
               typeName.equals("Email") ||
               typeName.equals("Address") ||
               typeName.matches("[A-Z][a-z]+"); // Single-word types often value objects
    }

    private String buildMessage(int mockCount, int testCount, double ratio, Set<String> valueObjectMocks) {
        StringBuilder message = new StringBuilder();
        message.append(String.format(
            "This test suite uses %d @Mock annotations across %d tests (%.2f mocks per test). ",
            mockCount,
            testCount,
            ratio
        ));

        if (!valueObjectMocks.isEmpty()) {
            message.append("Value objects detected as mocks: ");
            message.append(String.join(", ", valueObjectMocks));
            message.append(". ");
        }

        message.append("Consider using real instances for value objects and test-fixtures for complex dependency graphs.");

        return message.toString();
    }

    private String buildRecommendation() {
        return "Replace value object mocks with real instances. " +
               "For complex dependency setup, consider implementing test-fixtures (see Pattern 2 detection).";
    }
}
