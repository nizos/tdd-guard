package com.lightspeed.tddguard.junit5.integration;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that runs real JUnit 5 tests through the platform
 * and verifies TddGuardListener captures results correctly.
 *
 * Covers: End-to-end test execution and JSON output
 */
class IntegrationTest {

    private final Gson gson = new Gson();

    @Test
    void shouldCaptureRealTestExecutionAndWriteJson(@TempDir Path tempDir) throws IOException {
        // Given: TDDGUARD_ENABLED and project root configured
        System.setProperty("tddguard.projectRoot", tempDir.toString());

        // Create .claude/tdd-guard directory to enable TDD Guard
        Path tddGuardDir = tempDir.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // When: Running real tests via JUnit Platform
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(SampleTests.class))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should be created
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        assertTrue(Files.exists(testJsonPath), "test.json should be created");

        // And: JSON should have correct structure
        String json = Files.readString(testJsonPath);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        assertEquals("junit5", root.get("framework").getAsString());
        assertNotNull(root.get("timestamp"));
        assertTrue(root.get("duration").getAsLong() >= 0);

        // And: Summary should be correct (3 total: 1 passed, 1 failed, 1 skipped)
        JsonObject summary = root.getAsJsonObject("summary");
        assertEquals(3, summary.get("total").getAsInt());
        assertEquals(1, summary.get("passed").getAsInt());
        assertEquals(1, summary.get("failed").getAsInt());
        assertEquals(1, summary.get("skipped").getAsInt());

        // And: Tests array should have 3 tests
        JsonArray tests = root.getAsJsonArray("tests");
        assertEquals(3, tests.size());

        // And: Failures array should have 1 failure
        JsonArray failures = root.getAsJsonArray("failures");
        assertEquals(1, failures.size());

        // And: Educational should be empty
        JsonArray educational = root.getAsJsonArray("educational");
        assertEquals(0, educational.size());

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldNotCreateJsonWhenDisabled(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard is disabled (no env var, no .claude/tdd-guard directory)
        System.setProperty("tddguard.projectRoot", tempDir.toString());

        // When: Running tests
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(SampleTests.class))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should NOT be created
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        assertFalse(Files.exists(testJsonPath), "test.json should not be created when disabled");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldHandleNestedTests(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard enabled
        System.setProperty("tddguard.projectRoot", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".claude/tdd-guard"));

        // When: Running tests with nested structure
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(NestedTestExample.class))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should capture nested tests
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        String json = Files.readString(testJsonPath);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        JsonArray tests = root.getAsJsonArray("tests");
        assertTrue(tests.size() >= 2, "Should capture nested tests");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }
}
