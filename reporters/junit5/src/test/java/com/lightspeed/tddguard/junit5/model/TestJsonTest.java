package com.lightspeed.tddguard.junit5.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestJson model.
 *
 * Covers AC#4: Model classes match JSON schema
 */
class TestJsonTest {

    private final Gson gson = new GsonBuilder().create();

    @Test
    void shouldSerializeToCorrectJsonStructure() {
        // Given: A complete TestJson object
        TestJson testJson = new TestJson();
        testJson.timestamp = "2025-11-06T12:00:00Z";
        testJson.duration = 1500;
        testJson.summary = new TestSummary(3, 2, 1, 0);
        testJson.tests = List.of(
            new TestCase("test1", "src/test/MyTest.java", "passed", 500, "test1()")
        );
        testJson.failures = List.of(
            new TestFailure("test2", "src/test/MyTest.java", "Expected 5 but was 3", "stack trace here")
        );

        // When: Serializing to JSON
        String json = gson.toJson(testJson);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        // Then: Should have correct structure
        assertEquals("junit5", parsed.get("framework").getAsString());
        assertEquals("2025-11-06T12:00:00Z", parsed.get("timestamp").getAsString());
        assertEquals(1500, parsed.get("duration").getAsLong());

        assertTrue(parsed.has("summary"));
        assertTrue(parsed.has("tests"));
        assertTrue(parsed.has("failures"));
        assertTrue(parsed.has("educational"));
    }

    @Test
    void shouldHaveDefaultFrameworkValue() {
        // Given: A new TestJson object
        TestJson testJson = new TestJson();

        // Then: framework should be "junit5"
        assertEquals("junit5", testJson.framework);
    }

    @Test
    void shouldHaveEmptyEducationalArray() {
        // Given: A new TestJson object
        TestJson testJson = new TestJson();

        // Then: educational should be empty list
        assertNotNull(testJson.educational);
        assertTrue(testJson.educational.isEmpty());
    }
}
