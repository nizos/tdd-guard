package com.lightspeed.tddguard.junit5.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestFailure model.
 */
class TestFailureTest {

    private final Gson gson = new Gson();

    @Test
    void shouldSerializeAllFields() {
        // Given: A TestFailure with all fields
        TestFailure failure = new TestFailure(
            "testFailed",
            "src/test/MyTest.java",
            "Expected 5 but was 3",
            "java.lang.AssertionError: Expected 5 but was 3\n\tat MyTest.testFailed(MyTest.java:42)"
        );

        // When: Serializing to JSON
        String json = gson.toJson(failure);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        // Then: All fields should be present
        assertEquals("testFailed", parsed.get("name").getAsString());
        assertEquals("src/test/MyTest.java", parsed.get("file").getAsString());
        assertEquals("Expected 5 but was 3", parsed.get("message").getAsString());
        assertTrue(parsed.get("stack").getAsString().contains("AssertionError"));
    }

    @Test
    void shouldHandleMultilineStackTrace() {
        // Given: A failure with multiline stack trace
        String stackTrace = "java.lang.AssertionError: test failed\n" +
                           "\tat MyTest.testMethod(MyTest.java:10)\n" +
                           "\tat java.base/jdk.internal.reflect.Method.invoke(Method.java:567)";

        TestFailure failure = new TestFailure("test", "file", "msg", stackTrace);

        // When: Serializing
        String json = gson.toJson(failure);

        // Then: Stack trace should be preserved with newlines
        assertTrue(json.contains("\\n"));
    }
}
