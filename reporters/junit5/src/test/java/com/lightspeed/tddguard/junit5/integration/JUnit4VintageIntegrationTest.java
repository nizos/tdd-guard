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
 * Integration test verifying JUnit 4 tests are captured via JUnit Vintage Engine.
 *
 * Covers: AC#7 - JUnit 4 Vintage Support
 */
class JUnit4VintageIntegrationTest {

    private final Gson gson = new Gson();

    @Test
    void shouldCaptureJUnit4TestsViaVintageEngine(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard enabled
        System.setProperty("tddguard.projectRoot", tempDir.toString());
        Path tddGuardDir = tempDir.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // When: Running JUnit 4 tests via Vintage Engine
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(JUnit4VintageFixture.class))
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

        // And: Summary should include JUnit 4 tests (2 total: 1 passed, 1 failed)
        JsonObject summary = root.getAsJsonObject("summary");
        assertEquals(2, summary.get("total").getAsInt());
        assertEquals(1, summary.get("passed").getAsInt());
        assertEquals(1, summary.get("failed").getAsInt());
        assertEquals(0, summary.get("skipped").getAsInt());

        // And: Tests array should have 2 tests
        JsonArray tests = root.getAsJsonArray("tests");
        assertEquals(2, tests.size());

        // And: Should contain JUnit 4 test names
        boolean foundPassingTest = false;
        boolean foundFailingTest = false;
        for (int i = 0; i < tests.size(); i++) {
            JsonObject test = tests.get(i).getAsJsonObject();
            String name = test.get("name").getAsString();
            if (name.equals("junit4TestThatPasses")) {
                foundPassingTest = true;
                assertEquals("passed", test.get("status").getAsString());
            } else if (name.equals("junit4TestThatFails")) {
                foundFailingTest = true;
                assertEquals("failed", test.get("status").getAsString());
            }
        }
        assertTrue(foundPassingTest, "Should capture JUnit 4 passing test");
        assertTrue(foundFailingTest, "Should capture JUnit 4 failing test");

        // And: Failures array should have 1 failure
        JsonArray failures = root.getAsJsonArray("failures");
        assertEquals(1, failures.size());

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldCaptureMixedJUnit4And5Tests(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard enabled
        System.setProperty("tddguard.projectRoot", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".claude/tdd-guard"));

        // When: Running both JUnit 4 and JUnit 5 tests in same run
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(
                DiscoverySelectors.selectClass(JUnit4VintageFixture.class),
                DiscoverySelectors.selectClass(SampleTests.class)
            )
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should capture both JUnit 4 and JUnit 5 tests
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        String json = Files.readString(testJsonPath);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        // And: Summary should include both (5 total: 2 JUnit4 + 3 JUnit5)
        JsonObject summary = root.getAsJsonObject("summary");
        assertEquals(5, summary.get("total").getAsInt());
        assertEquals(2, summary.get("passed").getAsInt()); // 1 JUnit4 + 1 JUnit5
        assertEquals(2, summary.get("failed").getAsInt());  // 1 JUnit4 + 1 JUnit5
        assertEquals(1, summary.get("skipped").getAsInt()); // 0 JUnit4 + 1 JUnit5

        // And: Tests array should have 5 tests
        JsonArray tests = root.getAsJsonArray("tests");
        assertEquals(5, tests.size());

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }
}
