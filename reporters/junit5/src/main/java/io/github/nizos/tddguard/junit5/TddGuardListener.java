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
        } catch (IllegalStateException e) {
            System.err.println("[tdd-guard-junit5] disabled: " + e.getMessage());
            collector = null;
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult result) {
        if (collector == null || !testIdentifier.isTest()) return;

        String moduleId = moduleId(testIdentifier);
        String methodName = methodName(testIdentifier);

        switch (result.getStatus()) {
            case SUCCESSFUL:
                collector.recordPassed(moduleId, methodName);
                break;
            case FAILED:
            case ABORTED:
                collector.recordFailed(moduleId, methodName, result.getThrowable().orElse(null));
                break;
            default:
                break;
        }
    }

    @Override
    public void executionSkipped(TestIdentifier testIdentifier, String reason) {
        if (collector == null || !testIdentifier.isTest()) return;

        collector.recordSkipped(moduleId(testIdentifier), methodName(testIdentifier));
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
