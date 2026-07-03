package io.github.nizos.tddguard.junit5;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

import java.nio.file.Path;
import java.util.function.Function;

/**
 * JUnit5 Platform {@code TestExecutionListener} that writes test results
 * to {@code .claude/tdd-guard/data/test.json} under the configured project root.
 *
 * <p>Activation is controlled entirely by {@link ProjectRootResolver#ENV_VAR}:
 * <ul>
 *   <li>Env var unset: the listener is a no-op, never writes test.json.
 *       This keeps the jar safe to include on a classpath without opting in.</li>
 *   <li>Env var set and valid: results are captured and written.</li>
 *   <li>Env var set but invalid (e.g. cwd outside root): a warning is logged
 *       and the listener is disabled for this run.</li>
 * </ul>
 */
public final class TddGuardListener implements TestExecutionListener {
    static final String DATA_SUBPATH = ".claude/tdd-guard/data";

    private final ProjectRootResolver resolver;
    private final TestJsonWriter writer;
    private final Function<String, String> envAccessor;

    private TestResultCollector collector;
    private Path outputDir;
    private TestPlan testPlan;

    public TddGuardListener() {
        this(new ProjectRootResolver(), new TestJsonWriter(), System::getenv);
    }

    TddGuardListener(ProjectRootResolver resolver, TestJsonWriter writer, Function<String, String> envAccessor) {
        this.resolver = resolver;
        this.writer = writer;
        this.envAccessor = envAccessor;
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        String envValue = envAccessor.apply(ProjectRootResolver.ENV_VAR);
        if (envValue == null || envValue.isEmpty()) {
            return;
        }

        try {
            Path projectRoot = resolver.resolve(null);
            outputDir = projectRoot.resolve(DATA_SUBPATH);
            collector = new TestResultCollector();
            if (testPlan != null) {
                this.testPlan = testPlan;
                collector.setExpectedCount((int) testPlan.countTestIdentifiers(TestIdentifier::isTest));
            }
        } catch (IllegalStateException e) {
            System.err.println("[tdd-guard-junit5] disabled: " + e.getMessage());
            collector = null;
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (collector == null) return;

        if (testIdentifier.isTest()) {
            String moduleId = moduleId(testIdentifier);
            String methodName = methodName(testIdentifier);

            switch (result.getStatus()) {
                case SUCCESSFUL:
                    collector.recordPassed(moduleId, methodName);
                    break;
                case FAILED:
                case ABORTED:
                    // ABORTED = individual test aborted (e.g. Assumptions.assumeTrue(false)), not run interruption.
                    // It is counted in expectedCount and recorded here so recordedCount stays consistent.
                    collector.recordFailed(moduleId, methodName, result.getThrowable().orElse(null));
                    break;
                default:
                    break;
            }
            return;
        }

        if (result.getStatus() == TestExecutionResult.Status.FAILED) {
            result.getThrowable().ifPresent(collector::recordUnhandledError);
        } else if (result.getStatus() == TestExecutionResult.Status.ABORTED) {
            // An ABORTED container (e.g. Assumptions.assumeTrue(false) in @BeforeAll) means the
            // platform never fires events for that container's children. Walking descendants to
            // record them would fabricate outcomes that were never assigned, so we disable the
            // interrupted comparison for the rest of this run instead — the same approach
            // minitest takes when it cannot trust its expected count.
            collector.markCountUntrustworthy();
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (collector == null) return;

        if (testIdentifier.isTest()) {
            // Single @Disabled method: the platform fires executionSkipped directly on the
            // test identifier. Record it so recordedCount stays balanced with expectedCount.
            collector.recordSkipped(moduleId(testIdentifier), methodName(testIdentifier));
        } else if (testPlan != null) {
            // Whole @Disabled class: the platform fires executionSkipped on the container
            // and does NOT descend into its children. The children were counted in
            // expectedCount at discovery, so we record each one as skipped here to keep
            // the counts balanced — otherwise a normal run with a disabled class would
            // falsely report "interrupted".
            testPlan.getDescendants(testIdentifier).stream()
                    .filter(TestIdentifier::isTest)
                    .forEach(child -> collector.recordSkipped(moduleId(child), methodName(child)));
        }
    }

    @Override
    public void dynamicTestRegistered(TestIdentifier testIdentifier) {
        if (collector == null || !testIdentifier.isTest()) return;

        // @TestFactory and (on Jupiter 5.11+) @ParameterizedTest / @RepeatedTest invocations
        // are not in the up-front TestPlan, so they are missing from the initial expectedCount.
        // Growing the count here keeps recordedCount from exceeding expectedCount on a
        // completed dynamic run, which would otherwise hide a missing static test by making
        // the comparison meaningless.
        // Note: this does NOT make cut-short dynamic runs detectable — a killed JVM never
        // reaches testPlanExecutionFinished, so test.json is never written.
        collector.incrementExpectedCount();
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (collector == null) return;

        try {
            writer.write(outputDir, collector.build());
        } catch (Exception e) {
            System.err.println("[tdd-guard-junit5] failed to write test.json: " + e.getMessage());
        }
    }

    private static String moduleId(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(source -> source instanceof MethodSource)
                .map(source -> ((MethodSource) source).getClassName())
                .orElse("unknown");
    }

    private static String methodName(TestIdentifier testIdentifier) {
        return testIdentifier.getSource()
                .filter(source -> source instanceof MethodSource)
                .map(source -> ((MethodSource) source).getMethodName())
                .orElseGet(testIdentifier::getDisplayName);
    }
}
