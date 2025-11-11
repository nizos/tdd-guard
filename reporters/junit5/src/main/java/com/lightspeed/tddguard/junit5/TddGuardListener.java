package com.lightspeed.tddguard.junit5;

import com.lightspeed.tddguard.junit5.patterns.*;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * JUnit 5 Platform TestExecutionListener that captures test execution data for TDD Guard.
 *
 * This listener auto-discovers via service provider mechanism and enables itself only when:
 * - TDDGUARD_ENABLED environment variable is set to "true" (case-insensitive), OR
 * - .claude/tdd-guard/ directory exists in the project root
 *
 * When disabled, all lifecycle methods return immediately with minimal overhead (<100µs per test).
 */
public class TddGuardListener implements TestExecutionListener {

    private final boolean enabled;
    private final Path projectRoot;
    private TestResultCollector collector;
    private final TestJsonWriter jsonWriter;
    private final List<PatternDetector> patternDetectors;

    /**
     * Default constructor used by JUnit Platform service loader.
     * Uses system environment and auto-detects project root.
     */
    public TddGuardListener() {
        this(
            resolveProjectRoot(),
            () -> System.getenv("TDDGUARD_ENABLED"),
            () -> System.getProperty("tddguard.projectRoot")
        );
    }

    /**
     * Constructor for testing with dependency injection.
     *
     * @param projectRoot      The project root directory
     * @param envVarSupplier   Supplier for TDDGUARD_ENABLED environment variable
     * @param sysPropSupplier  Supplier for tddguard.projectRoot system property (unused in this constructor)
     */
    TddGuardListener(Path projectRoot, Supplier<String> envVarSupplier, Supplier<String> sysPropSupplier) {
        this.projectRoot = projectRoot;
        this.enabled = detectTddGuard(envVarSupplier);
        this.jsonWriter = new TestJsonWriter();

        // Create shared SourceDirectoryResolver and SourceAnalyzer
        SourceDirectoryResolver directoryResolver = new SourceDirectoryResolver(
            projectRoot,
            System::getProperty,
            System::getenv
        );
        SourceAnalyzer sourceAnalyzer = new SourceAnalyzer(directoryResolver);

        this.patternDetectors = List.of(
            new MockOveruseDetector(sourceAnalyzer),
            new TestFixturesOpportunityDetector(sourceAnalyzer),
            new MissingIsolationDetector(sourceAnalyzer),
            new GradleBuildOptimizationDetector(),
            new FileStructureAnalyzer(sourceAnalyzer)
        );
    }

    /**
     * Detects whether TDD Guard is present and listener should be enabled.
     *
     * @param envVarSupplier Supplier for TDDGUARD_ENABLED environment variable
     * @return true if TDD Guard is detected, false otherwise
     */
    private boolean detectTddGuard(Supplier<String> envVarSupplier) {
        // Check environment variable
        String envEnabled = envVarSupplier.get();
        if ("true".equalsIgnoreCase(envEnabled)) {
            return true;
        }

        // Check directory existence
        Path tddGuardDir = projectRoot.resolve(".claude/tdd-guard");
        return Files.exists(tddGuardDir);
    }

    /**
     * Returns whether this listener is enabled.
     *
     * @return true if enabled, false otherwise
     */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * Resolves the project root directory using fallback hierarchy:
     * 1. System property: tddguard.projectRoot
     * 2. Environment variable: TDDGUARD_PROJECT_ROOT
     * 3. Traverse up from working directory looking for build.gradle or pom.xml
     * 4. Fallback to current working directory
     *
     * @return The resolved project root path
     */
    private static Path resolveProjectRoot() {
        // 1. System property
        String sysProp = System.getProperty("tddguard.projectRoot");
        if (sysProp != null) {
            return Path.of(sysProp).toAbsolutePath();
        }

        // 2. Environment variable
        String envVar = System.getenv("TDDGUARD_PROJECT_ROOT");
        if (envVar != null) {
            return Path.of(envVar).toAbsolutePath();
        }

        // 3. Traverse up looking for build files
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("build.gradle")) ||
                Files.exists(current.resolve("build.gradle.kts")) ||
                Files.exists(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }

        // 4. Fallback to working directory
        return Path.of("").toAbsolutePath();
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (!enabled) return;
        try {
            // Create resolver with system property and environment variable suppliers
            SourceDirectoryResolver resolver = new SourceDirectoryResolver(
                projectRoot,
                System::getProperty,
                System::getenv
            );
            collector = new TestResultCollector(projectRoot, resolver);
            collector.testPlanStarted();
        } catch (Exception e) {
            // Never fail tests due to TDD Guard errors
            System.err.println("TDD Guard: Error starting test plan: " + e.getMessage());
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!enabled || collector == null) return;
        try {
            collector.recordTestStart(testIdentifier);
        } catch (Exception e) {
            System.err.println("TDD Guard: Error recording test start: " + e.getMessage());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!enabled || collector == null) return;
        try {
            collector.recordTestFinish(testIdentifier, testExecutionResult);
        } catch (Exception e) {
            System.err.println("TDD Guard: Error recording test finish: " + e.getMessage());
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (!enabled || collector == null) return;
        try {
            collector.recordSkipped(testIdentifier, reason);
        } catch (Exception e) {
            System.err.println("TDD Guard: Error recording skipped test: " + e.getMessage());
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (!enabled || collector == null) return;
        try {
            // Build TestJson for pattern detection
            com.lightspeed.tddguard.junit5.model.TestJson testJson =
                new com.lightspeed.tddguard.junit5.model.TestJson();
            testJson.tests = collector.getTests();
            testJson.failures = collector.getFailures();
            testJson.summary = calculateSummary(collector.getTests());

            // Collect build metrics
            BuildMetrics buildMetrics = BuildMetrics.collect(projectRoot);

            // Run pattern detectors
            List<EducationalFeedback> educational = new ArrayList<>();
            for (PatternDetector detector : patternDetectors) {
                detector.detect(testJson, projectRoot, buildMetrics)
                    .ifPresent(educational::add);
            }

            // Write test.json with educational feedback
            Path outputPath = projectRoot.resolve(".claude/tdd-guard/data/test.json");
            jsonWriter.write(
                outputPath,
                collector.getTests(),
                collector.getFailures(),
                collector.getTotalDuration(),
                educational
            );
        } catch (Exception e) {
            System.err.println("TDD Guard: Error writing test results: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private com.lightspeed.tddguard.junit5.model.TestSummary calculateSummary(
        List<com.lightspeed.tddguard.junit5.model.TestCase> tests
    ) {
        int total = tests.size();
        int passed = (int) tests.stream().filter(t -> "passed".equals(t.status)).count();
        int failed = (int) tests.stream().filter(t -> "failed".equals(t.status)).count();
        int skipped = (int) tests.stream().filter(t -> "skipped".equals(t.status)).count();
        return new com.lightspeed.tddguard.junit5.model.TestSummary(total, passed, failed, skipped);
    }
}
