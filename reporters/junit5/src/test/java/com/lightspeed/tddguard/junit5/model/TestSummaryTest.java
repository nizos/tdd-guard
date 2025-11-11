package com.lightspeed.tddguard.junit5.model;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestSummary model.
 */
class TestSummaryTest {

    private final Gson gson = new Gson();

    @Test
    void shouldSerializeAllFields() {
        // Given: A TestSummary with counts
        TestSummary summary = new TestSummary(10, 7, 2, 1);

        // When: Serializing to JSON
        String json = gson.toJson(summary);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        // Then: All fields should be present
        assertEquals(10, parsed.get("total").getAsInt());
        assertEquals(7, parsed.get("passed").getAsInt());
        assertEquals(2, parsed.get("failed").getAsInt());
        assertEquals(1, parsed.get("skipped").getAsInt());
    }
}
