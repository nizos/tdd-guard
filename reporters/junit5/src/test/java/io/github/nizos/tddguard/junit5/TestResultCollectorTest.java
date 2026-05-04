package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;
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
}
