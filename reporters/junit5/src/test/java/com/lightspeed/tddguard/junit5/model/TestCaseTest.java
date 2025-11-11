package com.lightspeed.tddguard.junit5.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestCase model.
 */
class TestCaseTest {

    private final Gson gson = new Gson();

    @Test
    void shouldSerializeAllFields() {
        // Given: A TestCase with all fields
        TestCase testCase = new TestCase(
            "testMethod",
            "src/test/java/MyTest.java",
            "passed",
            250,
            "testMethod()"
        );

        // When: Serializing to JSON
        String json = gson.toJson(testCase);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        // Then: All fields should be present
        assertEquals("testMethod", parsed.get("name").getAsString());
        assertEquals("src/test/java/MyTest.java", parsed.get("file").getAsString());
        assertEquals("passed", parsed.get("status").getAsString());
        assertEquals(250, parsed.get("duration").getAsLong());
        assertEquals("testMethod()", parsed.get("displayName").getAsString());
    }

    @Test
    void shouldSupportAllStatuses() {
        // Given: Test cases with different statuses
        TestCase passed = new TestCase("t1", "file", "passed", 0, "t1");
        TestCase failed = new TestCase("t2", "file", "failed", 0, "t2");
        TestCase skipped = new TestCase("t3", "file", "skipped", 0, "t3");

        // Then: All statuses should be valid
        assertEquals("passed", passed.status);
        assertEquals("failed", failed.status);
        assertEquals("skipped", skipped.status);
    }
}
