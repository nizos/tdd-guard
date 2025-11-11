package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Detects Gradle build optimization issues.
 * Measures build time variance and identifies incremental compilation problems.
 * All feedback is fact-based with exact measurements.
 */
public class GradleBuildOptimizationDetector implements PatternDetector {

    private static final double VARIANCE_THRESHOLD = 20.0;
    private static final int MINIMUM_BUILD_HISTORY = 3;

    @Override
    public String getCategory() {
        return "gradle-incremental-compilation";
    }

    @Override
    public Optional<EducationalFeedback> detect(
        TestJson testResults,
        Path projectRoot,
        BuildMetrics buildMetrics
    ) {
        List<Long> buildTimes = buildMetrics.getBuildTimeHistory();

        // Need at least 3 builds to calculate meaningful variance
        if (buildTimes.size() < MINIMUM_BUILD_HISTORY) {
            return Optional.empty();
        }

        double avgTime = buildTimes.stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0.0);

        double variance = calculateVariancePercent(buildTimes, avgTime);

        // Only trigger if variance exceeds threshold
        if (variance <= VARIANCE_THRESHOLD) {
            return Optional.empty();
        }

        boolean incrementalEnabled = isIncrementalCompilationEnabled(projectRoot);
        List<String> annotationProcessors = detectAnnotationProcessors(projectRoot);

        Map<String, Object> evidence = new HashMap<>();
        evidence.put("variancePercent", String.format("%.1f", variance));
        evidence.put("incrementalEnabled", incrementalEnabled);
        evidence.put("annotationProcessors", annotationProcessors);
        evidence.put("buildTimes", new ArrayList<>(buildTimes));

        String message = buildMessage(variance, incrementalEnabled, annotationProcessors);
        String recommendation = buildRecommendation(incrementalEnabled, annotationProcessors);

        return Optional.of(new EducationalFeedback(
            "gradle-incremental-compilation",
            "info",
            "Gradle incremental compilation issue detected",
            evidence,
            message,
            recommendation
        ));
    }

    /**
     * Calculates variance as percentage of average.
     * Variance = (max deviation from average / average) * 100
     */
    private double calculateVariancePercent(List<Long> times, double avg) {
        double maxDeviation = times.stream()
            .mapToDouble(t -> Math.abs(t - avg))
            .max()
            .orElse(0.0);
        return (maxDeviation / avg) * 100.0;
    }

    /**
     * Checks if incremental compilation is enabled in gradle.properties.
     */
    private boolean isIncrementalCompilationEnabled(Path projectRoot) {
        Path gradleProperties = projectRoot.resolve("gradle.properties");
        if (!Files.exists(gradleProperties)) {
            return false;
        }

        try {
            String content = Files.readString(gradleProperties);
            return content.contains("org.gradle.caching=true") &&
                   !content.contains("org.gradle.caching=false");
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Detects annotation processors from build.gradle or build.gradle.kts.
     */
    private List<String> detectAnnotationProcessors(Path projectRoot) {
        Path buildGradle = projectRoot.resolve("build.gradle");
        if (!Files.exists(buildGradle)) {
            buildGradle = projectRoot.resolve("build.gradle.kts");
        }

        if (!Files.exists(buildGradle)) {
            return List.of();
        }

        try {
            String content = Files.readString(buildGradle);
            List<String> processors = new ArrayList<>();

            if (content.contains("lombok")) processors.add("Lombok");
            if (content.contains("mapstruct")) processors.add("MapStruct");
            if (content.contains("jpa-modelgen")) processors.add("JPA Metamodel");
            if (content.contains("querydsl")) processors.add("QueryDSL");

            return processors;
        } catch (IOException e) {
            return List.of();
        }
    }

    private String buildMessage(double variance, boolean incrementalEnabled, List<String> processors) {
        StringBuilder message = new StringBuilder();
        message.append(String.format(
            "Build time variance: %.1f%% (expected: <10%% for incremental builds). ",
            variance
        ));
        message.append(String.format("Incremental compilation enabled: %s. ", incrementalEnabled));

        if (processors.isEmpty()) {
            message.append("Annotation processors detected: none. ");
        } else {
            message.append("Annotation processors detected: ");
            message.append(String.join(", ", processors));
            message.append(". ");
        }

        message.append("High variance suggests incremental compilation not working effectively.");

        return message.toString();
    }

    private String buildRecommendation(boolean incrementalEnabled, List<String> processors) {
        StringBuilder rec = new StringBuilder();

        if (!incrementalEnabled) {
            rec.append("Enable Gradle build cache in gradle.properties:\n");
            rec.append("  org.gradle.caching=true\n");
            rec.append("  org.gradle.parallel=true\n");
        }

        if (!processors.isEmpty()) {
            if (rec.length() > 0) {
                rec.append("\n");
            }
            rec.append("Annotation processors detected: ");
            rec.append(String.join(", ", processors));
            rec.append(". Ensure processors are properly configured with isolating annotation processor path ");
            rec.append("to maintain incremental compilation. See Gradle docs on annotation processor configuration.");
        }

        if (rec.length() == 0) {
            rec.append("Investigate build scripts for tasks that bypass incremental compilation. ");
            rec.append("Check for tasks using inputs.files() without proper up-to-date checking.");
        }

        return rec.toString();
    }
}
