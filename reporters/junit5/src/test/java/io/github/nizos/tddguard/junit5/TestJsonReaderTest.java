package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestError;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TestJsonReaderTest {

    private final TestJsonWriter writer = new TestJsonWriter();
    private final TestJsonReader reader = new TestJsonReader();

    @Test
    void parsesModuleIdFromSingleModule() {
        TestResult result = new TestResult(List.of(
                new TestModule("MyTest", List.of(
                        TestCase.failed("CompilationError", "MyTest::CompilationError",
                                List.of(new TestError("error: package does not exist")))
                ))
        ), "failed");

        String json = writer.serialize(result);
        TestResult parsed = reader.parse(json);

        assertEquals(1, parsed.testModules().size());
        assertEquals("MyTest", parsed.testModules().get(0).moduleId());
    }

    @Test
    void parsesReasonField() {
        TestResult result = new TestResult(List.of(
                new TestModule("MyTest", List.of(TestCase.passed("myTest", "MyTest::myTest")))
        ), "passed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals("passed", parsed.reason());
    }

    @Test
    void parsesFailedReasonField() {
        TestResult result = new TestResult(List.of(
                new TestModule("MyTest", List.of(
                        TestCase.failed("CompilationError", "MyTest::CompilationError",
                                List.of(new TestError("error")))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals("failed", parsed.reason());
    }

    @Test
    void parsesTestCaseName() {
        TestResult result = new TestResult(List.of(
                new TestModule("MyTest", List.of(
                        TestCase.failed("CompilationError", "MyTest::CompilationError",
                                List.of(new TestError("msg")))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals("CompilationError", parsed.testModules().get(0).tests().get(0).name());
    }

    @Test
    void parsesTestCaseFullName() {
        TestResult result = new TestResult(List.of(
                new TestModule("ATest", List.of(
                        TestCase.failed("CompilationError", "ATest::CompilationError",
                                List.of(new TestError("msg")))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals("ATest::CompilationError", parsed.testModules().get(0).tests().get(0).fullName());
    }

    @Test
    void parsesFailedState() {
        TestResult result = new TestResult(List.of(
                new TestModule("T", List.of(
                        TestCase.failed("CompilationError", "T::CompilationError",
                                List.of(new TestError("msg")))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals(TestCase.State.FAILED, parsed.testModules().get(0).tests().get(0).state());
    }

    @Test
    void parsesErrorMessage() {
        String errorMsg = "error: package com.example does not exist";
        TestResult result = new TestResult(List.of(
                new TestModule("MyTest", List.of(
                        TestCase.failed("CompilationError", "MyTest::CompilationError",
                                List.of(new TestError(errorMsg)))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals(errorMsg, parsed.testModules().get(0).tests().get(0).errors().get(0).message());
    }

    @Test
    void parsesErrorMessageWithSpecialCharacters() {
        String errorMsg = "error: \"quotes\" and\\backslash and\nnewline";
        TestResult result = new TestResult(List.of(
                new TestModule("T", List.of(
                        TestCase.failed("CompilationError", "T::CompilationError",
                                List.of(new TestError(errorMsg)))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals(errorMsg, parsed.testModules().get(0).tests().get(0).errors().get(0).message());
    }

    @Test
    void parsesMultipleModules() {
        TestResult result = new TestResult(List.of(
                new TestModule("ATest", List.of(
                        TestCase.failed("CompilationError", "ATest::CompilationError",
                                List.of(new TestError("error in A")))
                )),
                new TestModule("BTest", List.of(
                        TestCase.failed("CompilationError", "BTest::CompilationError",
                                List.of(new TestError("error in B")))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals(2, parsed.testModules().size());
        assertEquals("ATest", parsed.testModules().get(0).moduleId());
        assertEquals("BTest", parsed.testModules().get(1).moduleId());
    }

    @Test
    void parsesEmptyTestModules() {
        TestResult result = new TestResult(List.of(), null);

        TestResult parsed = reader.parse(writer.serialize(result));

        assertTrue(parsed.testModules().isEmpty());
    }


    @Test
    void parsesLongErrorMessageWithoutStackOverflow() {
        StringBuilder longMessage = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            if (i > 0) longMessage.append("\n");
            longMessage.append("    at com.example.Class").append(i)
                    .append(".method(Class").append(i).append(".java:").append(i + 1).append(")");
        }
        String errorMsg = longMessage.toString();
        TestResult result = new TestResult(List.of(
                new TestModule("T", List.of(
                        TestCase.failed("CompilationError", "T::CompilationError",
                                List.of(new TestError(errorMsg)))
                ))
        ), "failed");

        TestResult parsed = reader.parse(writer.serialize(result));

        assertEquals(errorMsg, parsed.testModules().get(0).tests().get(0).errors().get(0).message());
    }
}