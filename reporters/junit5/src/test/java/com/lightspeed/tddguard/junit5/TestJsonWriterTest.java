package com.lightspeed.tddguard.junit5;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.lightspeed.tddguard.junit5.model.TestCase;
import com.lightspeed.tddguard.junit5.model.TestFailure;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TestJsonWriter.
 *
 * Covers AC#8: JSON Output Format and AC#9: Atomic File Write
 */
class TestJsonWriterTest {

    private final Gson gson = new Gson();

    @Test
    void shouldWriteCorrectJsonStructure(@TempDir Path tempDir) throws IOException {
        // Given: Test results
        List<TestCase> tests = List.of(
            new TestCase("test1", "src/test/Test.java", "passed", 100, "test1()")
        );
        List<TestFailure> failures = List.of();
        long duration = 500;

        // When: Writing to JSON
        TestJsonWriter writer = new TestJsonWriter();
        Path outputPath = tempDir.resolve("test.json");
        writer.write(outputPath, tests, failures, duration);

        // Then: File should exist with correct structure
        assertTrue(Files.exists(outputPath));

        String json = Files.readString(outputPath);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        assertEquals("junit5", parsed.get("framework").getAsString());
        assertTrue(parsed.has("timestamp"));
        assertEquals(500, parsed.get("duration").getAsLong());
        assertTrue(parsed.has("summary"));
        assertTrue(parsed.has("tests"));
        assertTrue(parsed.has("failures"));
        assertTrue(parsed.has("educational"));
    }

    @Test
    void shouldWriteTimestampInIso8601Format(@TempDir Path tempDir) throws IOException {
        // Given: Test results
        TestJsonWriter writer = new TestJsonWriter();
        Path outputPath = tempDir.resolve("test.json");

        // When: Writing
        writer.write(outputPath, List.of(), List.of(), 0);

        // Then: Timestamp should be ISO 8601 format
        String json = Files.readString(outputPath);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        String timestamp = parsed.get("timestamp").getAsString();
        // Should be able to parse as Instant
        assertDoesNotThrow(() -> Instant.parse(timestamp));
    }

    @Test
    void shouldCalculateCorrectSummary(@TempDir Path tempDir) throws IOException {
        // Given: Mixed test results
        List<TestCase> tests = List.of(
            new TestCase("t1", "file", "passed", 0, "t1"),
            new TestCase("t2", "file", "passed", 0, "t2"),
            new TestCase("t3", "file", "failed", 0, "t3"),
            new TestCase("t4", "file", "skipped", 0, "t4")
        );

        // When: Writing
        TestJsonWriter writer = new TestJsonWriter();
        Path outputPath = tempDir.resolve("test.json");
        writer.write(outputPath, tests, List.of(), 1000);

        // Then: Summary should be correct
        String json = Files.readString(outputPath);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);
        JsonObject summary = parsed.getAsJsonObject("summary");

        assertEquals(4, summary.get("total").getAsInt());
        assertEquals(2, summary.get("passed").getAsInt());
        assertEquals(1, summary.get("failed").getAsInt());
        assertEquals(1, summary.get("skipped").getAsInt());
    }

    @Test
    void shouldWriteEducationalAsEmptyArray(@TempDir Path tempDir) throws IOException {
        // Given: Test results
        TestJsonWriter writer = new TestJsonWriter();
        Path outputPath = tempDir.resolve("test.json");

        // When: Writing
        writer.write(outputPath, List.of(), List.of(), 0);

        // Then: educational should be empty array
        String json = Files.readString(outputPath);
        JsonObject parsed = gson.fromJson(json, JsonObject.class);

        assertTrue(parsed.getAsJsonArray("educational").isEmpty());
    }

    @Test
    void shouldCreateParentDirectories(@TempDir Path tempDir) throws IOException {
        // Given: Output path with non-existent parent directories
        Path outputPath = tempDir.resolve("nested/dir/test.json");
        assertFalse(Files.exists(outputPath.getParent()));

        // When: Writing
        TestJsonWriter writer = new TestJsonWriter();
        writer.write(outputPath, List.of(), List.of(), 0);

        // Then: Parent directories should be created
        assertTrue(Files.exists(outputPath.getParent()));
        assertTrue(Files.exists(outputPath));
    }

    @Test
    void shouldUseAtomicWrite(@TempDir Path tempDir) throws IOException {
        // Given: Output path
        Path outputPath = tempDir.resolve("test.json");
        Path tempFile = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");

        // When: Writing (we can't directly test atomicity, but we can verify temp file is cleaned up)
        TestJsonWriter writer = new TestJsonWriter();
        writer.write(outputPath, List.of(), List.of(), 0);

        // Then: Temp file should not exist (was moved to final location)
        assertFalse(Files.exists(tempFile), "Temporary file should be cleaned up");
        assertTrue(Files.exists(outputPath), "Final file should exist");
    }

    @Test
    void shouldOverwriteExistingFile(@TempDir Path tempDir) throws IOException {
        // Given: Existing file with old content
        Path outputPath = tempDir.resolve("test.json");
        Files.writeString(outputPath, "old content");

        // When: Writing new content
        TestJsonWriter writer = new TestJsonWriter();
        List<TestCase> tests = List.of(
            new TestCase("newTest", "file", "passed", 0, "newTest")
        );
        writer.write(outputPath, tests, List.of(), 100);

        // Then: Should have new content
        String json = Files.readString(outputPath);
        assertTrue(json.contains("newTest"));
        assertFalse(json.contains("old content"));
    }

    @Test
    void shouldUseUtf8Encoding(@TempDir Path tempDir) throws IOException {
        // Given: Test with unicode characters
        List<TestCase> tests = List.of(
            new TestCase("testUnicode", "file", "passed", 0, "测试 тест test")
        );

        // When: Writing
        TestJsonWriter writer = new TestJsonWriter();
        Path outputPath = tempDir.resolve("test.json");
        writer.write(outputPath, tests, List.of(), 0);

        // Then: Should read back correctly with UTF-8
        String json = Files.readString(outputPath);
        assertTrue(json.contains("测试 тест test"));
    }
}
