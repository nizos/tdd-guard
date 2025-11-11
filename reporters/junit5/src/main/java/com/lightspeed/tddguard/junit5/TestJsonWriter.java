package com.lightspeed.tddguard.junit5;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lightspeed.tddguard.junit5.model.TestCase;
import com.lightspeed.tddguard.junit5.model.TestFailure;
import com.lightspeed.tddguard.junit5.model.TestJson;
import com.lightspeed.tddguard.junit5.model.TestSummary;
import com.lightspeed.tddguard.junit5.patterns.model.EducationalFeedback;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;

/**
 * Writes test results to JSON file in TDD Guard format.
 * Uses atomic write (temp file + rename) to prevent partial writes.
 */
class TestJsonWriter {

    private final Gson gson = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    /**
     * Writes test results to JSON file.
     *
     * @param outputPath Output file path
     * @param tests      List of test cases
     * @param failures   List of test failures
     * @param duration   Total test execution duration in milliseconds
     * @throws IOException If file write fails
     */
    void write(Path outputPath, List<TestCase> tests, List<TestFailure> failures, long duration) throws IOException {
        write(outputPath, tests, failures, duration, List.of());
    }

    /**
     * Writes test results to JSON file with educational feedback.
     *
     * @param outputPath          Output file path
     * @param tests               List of test cases
     * @param failures            List of test failures
     * @param duration            Total test execution duration in milliseconds
     * @param educationalFeedback List of educational feedback about detected patterns
     * @throws IOException If file write fails
     */
    void write(Path outputPath, List<TestCase> tests, List<TestFailure> failures, long duration,
               List<EducationalFeedback> educationalFeedback) throws IOException {
        // Build TestJson object
        TestJson testJson = new TestJson();
        testJson.timestamp = Instant.now().toString();
        testJson.duration = duration;
        testJson.summary = calculateSummary(tests);
        testJson.tests = tests;
        testJson.failures = failures;
        testJson.educational = educationalFeedback;

        // Ensure parent directories exist
        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }

        // Atomic write: temp file + rename
        Path tempFile = outputPath.resolveSibling(outputPath.getFileName() + ".tmp");

        try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
            gson.toJson(testJson, writer);
        }

        // Atomic rename
        Files.move(tempFile, outputPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private TestSummary calculateSummary(List<TestCase> tests) {
        int total = tests.size();
        int passed = (int) tests.stream().filter(t -> "passed".equals(t.status)).count();
        int failed = (int) tests.stream().filter(t -> "failed".equals(t.status)).count();
        int skipped = (int) tests.stream().filter(t -> "skipped".equals(t.status)).count();

        return new TestSummary(total, passed, failed, skipped);
    }
}
