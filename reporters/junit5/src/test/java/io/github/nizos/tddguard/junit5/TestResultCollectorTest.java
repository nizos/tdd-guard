package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;
import io.github.nizos.tddguard.junit5.model.UnhandledError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestResultCollectorTest {

    private final TestResultCollector collector = new TestResultCollector();

    @Test
    void buildsEmptyResultWhenNoTestsRecorded() {
        TestResult result = collector.build();

        assertTrue(result.testModules().isEmpty());
    }

    @Test
    void recordsPassedTestWithFullName() {
        collector.recordPassed("com.example.MyTest", "shouldWork");

        TestResult result = collector.build();

        assertEquals(1, result.testModules().size());
        TestModule module = result.testModules().get(0);
        assertEquals("com.example.MyTest", module.moduleId());
        assertEquals(1, module.tests().size());
        TestCase test = module.tests().get(0);
        assertEquals("shouldWork", test.name());
        assertEquals("com.example.MyTest::shouldWork", test.fullName());
        assertEquals(TestCase.State.PASSED, test.state());
        assertNull(test.errors());
    }

    @Test
    void recordsFailedTestWithThrowableMessage() {
        RuntimeException ex = new RuntimeException("expected true but was false");

        collector.recordFailed("com.example.MyTest", "shouldFail", ex);

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertEquals(TestCase.State.FAILED, test.state());
        assertEquals(1, test.errors().size());
        assertEquals("expected true but was false", test.errors().get(0).message());
    }

    @Test
    void fallsBackToClassNameWhenThrowableHasNoMessage() {
        Throwable throwable = new NullPointerException();

        collector.recordFailed("com.example.MyTest", "method", throwable);

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertEquals("java.lang.NullPointerException", test.errors().get(0).message());
    }

    @Test
    void fallsBackToDefaultMessageWhenThrowableIsNull() {
        collector.recordFailed("com.example.MyTest", "method", null);

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertEquals("Test failed without a throwable", test.errors().get(0).message());
    }

    @Test
    void recordsSkippedTest() {
        collector.recordSkipped("com.example.MyTest", "ignored");

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertEquals(TestCase.State.SKIPPED, test.state());
        assertNull(test.errors());
    }

    @Test
    void groupsTestsByModuleId() {
        collector.recordPassed("com.example.FirstTest", "a");
        collector.recordPassed("com.example.FirstTest", "b");
        collector.recordPassed("com.example.SecondTest", "c");

        List<TestModule> modules = collector.build().testModules();

        assertEquals(2, modules.size());
        assertEquals("com.example.FirstTest", modules.get(0).moduleId());
        assertEquals(2, modules.get(0).tests().size());
        assertEquals("com.example.SecondTest", modules.get(1).moduleId());
        assertEquals(1, modules.get(1).tests().size());
    }

    @Test
    void preservesInsertionOrder() {
        collector.recordPassed("com.example.SecondTest", "c");
        collector.recordPassed("com.example.FirstTest", "a");

        List<TestModule> modules = collector.build().testModules();

        assertEquals("com.example.SecondTest", modules.get(0).moduleId());
        assertEquals("com.example.FirstTest", modules.get(1).moduleId());
    }

    @Test
    void mixesPassedFailedAndSkippedInSameModule() {
        collector.recordPassed("com.example.MyTest", "pass");
        collector.recordFailed("com.example.MyTest", "fail", new RuntimeException("boom"));
        collector.recordSkipped("com.example.MyTest", "skip");

        TestModule module = collector.build().testModules().get(0);

        assertEquals(3, module.tests().size());
        assertEquals(TestCase.State.PASSED, module.tests().get(0).state());
        assertEquals(TestCase.State.FAILED, module.tests().get(1).state());
        assertEquals(TestCase.State.SKIPPED, module.tests().get(2).state());
    }

    @Test
    void reasonIsPassedWhenAllTestsPass() {
        collector.recordPassed("com.example.MyTest", "a");
        collector.recordPassed("com.example.MyTest", "b");

        assertEquals("passed", collector.build().reason());
    }
    @Test
    void reasonIsFailedWhenAnyTestFails() {
        collector.recordPassed("com.example.MyTest", "a");
        collector.recordFailed("com.example.MyTest", "b", new RuntimeException("boom"));

        assertEquals("failed", collector.build().reason());
    }

    @Test
    void reasonIsPassedWhenNoTestsRecorded() {
        assertEquals("passed", collector.build().reason());
    }

    @Test
    void reasonIsInterruptedWhenFewerTestsRecordedThanExpected() {
        collector.setExpectedCount(3);
        collector.recordPassed("com.example.MyTest", "a");
        collector.recordPassed("com.example.MyTest", "b");
        // only 2 recorded, expected 3

        assertEquals("interrupted", collector.build().reason());
    }

    @Test
    void reasonIsFailedTakesPriorityOverInterrupted() {
        collector.setExpectedCount(3);
        collector.recordFailed("com.example.MyTest", "a", new RuntimeException("boom"));
        // 1 recorded, expected 3 — but failed takes priority

        assertEquals("failed", collector.build().reason());
    }

    @Test
    void reasonIsPassedWhenExpectedCountMatchesRecorded() {
        collector.setExpectedCount(2);
        collector.recordPassed("com.example.MyTest", "a");
        collector.recordPassed("com.example.MyTest", "b");

        assertEquals("passed", collector.build().reason());
    }

    @Test
    void reasonIsPassedWhenExpectedCountIsZero() {
        collector.setExpectedCount(0);
        // expectedCount=0 means "no information" — do not emit interrupted

        assertEquals("passed", collector.build().reason());
    }

    @Test
    void populatesStackFromFirstUserFrame() {
        Throwable t = new RuntimeException("boom");
        t.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.opentest4j.AssertionFailedError", "<init>", "AssertionFailedError.java", 33),
                new StackTraceElement("org.junit.jupiter.api.AssertionUtils", "fail", "AssertionUtils.java", 39),
                new StackTraceElement("com.example.MyTest", "shouldFail", "MyTest.java", 12)
        });

        collector.recordFailed("com.example.MyTest", "shouldFail", t);

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertEquals("com.example.MyTest.shouldFail(MyTest.java:12)", test.errors().get(0).stack());
    }

    @Test
    void leavesStackNullWhenAllFramesAreFrameworkInternal() {
        Throwable t = new RuntimeException("boom");
        t.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.junit.jupiter.api.AssertionUtils", "fail", "AssertionUtils.java", 39),
                new StackTraceElement("jdk.internal.reflect.DirectMethodHandleAccessor", "invoke", "DirectMethodHandleAccessor.java", 103)
        });

        collector.recordFailed("com.example.MyTest", "shouldFail", t);

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertNull(test.errors().get(0).stack());
    }

    @Test
    void leavesStackNullWhenThrowableIsNull() {
        collector.recordFailed("com.example.MyTest", "shouldFail", null);

        TestCase test = collector.build().testModules().get(0).tests().get(0);
        assertNull(test.errors().get(0).stack());
    }

    @Test
    void buildHasEmptyUnhandledErrorsByDefault() {
        assertTrue(collector.build().unhandledErrors().isEmpty());
    }

    @Test
    void recordUnhandledErrorCapturesClassMessageAndStack() {
        Throwable t = new RuntimeException("teardown blew up");
        t.setStackTrace(new StackTraceElement[] {
                new StackTraceElement("org.junit.jupiter.engine.execution.ExecutableInvoker", "invoke", "ExecutableInvoker.java", 115),
                new StackTraceElement("com.example.MyTest", "tearDownAll", "MyTest.java", 31)
        });

        collector.recordUnhandledError(t);

        List<UnhandledError> errors = collector.build().unhandledErrors();
        assertEquals(1, errors.size());
        UnhandledError error = errors.get(0);
        assertEquals("java.lang.RuntimeException", error.name());
        assertEquals("teardown blew up", error.message());
        assertEquals("com.example.MyTest.tearDownAll(MyTest.java:31)", error.stack());
    }

    @Test
    void recordUnhandledErrorFallsBackToClassNameWhenMessageIsNull() {
        collector.recordUnhandledError(new NullPointerException());

        UnhandledError error = collector.build().unhandledErrors().get(0);
        assertEquals("java.lang.NullPointerException", error.name());
        assertEquals("java.lang.NullPointerException", error.message());
    }

    @Test
    void recordUnhandledErrorIgnoresNullThrowable() {
        collector.recordUnhandledError(null);

        assertTrue(collector.build().unhandledErrors().isEmpty());
    }

    @Test
    void recordUnhandledErrorAppendsInOrder() {
        collector.recordUnhandledError(new RuntimeException("first"));
        collector.recordUnhandledError(new IllegalStateException("second"));

        List<UnhandledError> errors = collector.build().unhandledErrors();
        assertEquals(2, errors.size());
        assertEquals("first", errors.get(0).message());
        assertEquals("second", errors.get(1).message());
    }

    @Test
    void reasonIsFailedWhenOnlyUnhandledErrorsPresent() {
        collector.recordPassed("com.example.MyTest", "a");
        collector.recordUnhandledError(new RuntimeException("hook failure"));

        assertEquals("failed", collector.build().reason());
    }

    @Test
    void reasonIsPassedWhenCountMarkedUntrustworthy() {
        // An aborted container (e.g. Assumptions.assumeTrue(false) in @BeforeAll) causes
        // the platform to fire no child events, so recordedCount < expectedCount — but the
        // count is not a reliable signal. Marking it untrustworthy disables the interrupted
        // branch so the run reports "passed" instead.
        collector.setExpectedCount(2);
        collector.recordPassed("com.example.MyTest", "a");
        collector.markCountUntrustworthy();

        assertEquals("passed", collector.build().reason());
    }

    @Test
    void markCountUntrustworthyIsSticky() {
        // Once the flag is set, later increments from dynamic test registrations cannot
        // re-enable the interrupted comparison.
        collector.setExpectedCount(2);
        collector.recordPassed("com.example.MyTest", "a");
        collector.markCountUntrustworthy();
        collector.incrementExpectedCount(); // simulate a dynamic test registering afterwards

        assertEquals("passed", collector.build().reason());
    }
}
