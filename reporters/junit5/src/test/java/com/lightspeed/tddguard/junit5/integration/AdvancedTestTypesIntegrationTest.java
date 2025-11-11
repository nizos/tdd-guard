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
 * Integration test for AC#7: Support all JUnit 5 test types.
 */
class AdvancedTestTypesIntegrationTest {

    private final Gson gson = new Gson();

    @Test
    void shouldCaptureParameterizedTests(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard enabled
        System.setProperty("tddguard.projectRoot", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".claude/tdd-guard"));

        // When: Running parameterized tests
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectClass(AdvancedTestTypes.class))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should capture all test invocations
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        String json = Files.readString(testJsonPath);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        JsonArray tests = root.getAsJsonArray("tests");

        // Parameterized test runs 3 times + repeated test 3 times + dynamic test factory with 2 tests = 8 total
        assertTrue(tests.size() >= 8,
            "Should capture all test invocations. Got: " + tests.size());

        // And: Summary should count all invocations
        JsonObject summary = root.getAsJsonObject("summary");
        assertTrue(summary.get("total").getAsInt() >= 8,
            "Summary should count all test invocations");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldCaptureRepeatedTests(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard enabled
        System.setProperty("tddguard.projectRoot", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".claude/tdd-guard"));

        // When: Running repeated tests
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectMethod(
                AdvancedTestTypes.class,
                "repeatedTest"
            ))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should have 3 test executions (one per repetition)
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        String json = Files.readString(testJsonPath);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        JsonArray tests = root.getAsJsonArray("tests");
        assertEquals(3, tests.size(), "Should capture all 3 repetitions");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }

    @Test
    void shouldCaptureDynamicTests(@TempDir Path tempDir) throws IOException {
        // Given: TDD Guard enabled
        System.setProperty("tddguard.projectRoot", tempDir.toString());
        Files.createDirectories(tempDir.resolve(".claude/tdd-guard"));

        // When: Running dynamic tests
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
            .selectors(DiscoverySelectors.selectMethod(
                AdvancedTestTypes.class,
                "dynamicTests"
            ))
            .build();

        Launcher launcher = LauncherFactory.create();
        launcher.execute(request);

        // Then: test.json should have 2 dynamic tests
        Path testJsonPath = tempDir.resolve(".claude/tdd-guard/data/test.json");
        String json = Files.readString(testJsonPath);
        JsonObject root = gson.fromJson(json, JsonObject.class);

        JsonArray tests = root.getAsJsonArray("tests");
        assertEquals(2, tests.size(), "Should capture both dynamic tests");

        // Cleanup
        System.clearProperty("tddguard.projectRoot");
    }
}
