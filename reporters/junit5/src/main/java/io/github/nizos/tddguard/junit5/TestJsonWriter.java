package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestError;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Serializes {@link TestResult} to the canonical TDD Guard JSON schema
 * and writes it atomically to {@code test.json}.
 */
public final class TestJsonWriter {
    private static final String DATA_FILE = "test.json";

    /**
     * Writes the result to {@code outputDir/test.json} using a temp file
     * and atomic rename to avoid partial writes.
     */
    public void write(Path outputDir, TestResult result) throws IOException {
        Files.createDirectories(outputDir);
        Path target = outputDir.resolve(DATA_FILE);
        Path temp = outputDir.resolve(DATA_FILE + ".tmp");

        try (Writer w = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
            w.write(serialize(result));
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Serializes a result to the canonical JSON string.
     * Exposed for testing.
     */
    String serialize(TestResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"testModules\": ");
        appendModules(sb, result.testModules());
        if (result.reason() != null) {
            sb.append(",\n  \"reason\": ").append(quote(result.reason()));
        }
        sb.append("\n}\n");
        return sb.toString();
    }

    private void appendModules(StringBuilder sb, List<TestModule> modules) {
        if (modules.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < modules.size(); i++) {
            appendModule(sb, modules.get(i));
            if (i < modules.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]");
    }

    private void appendModule(StringBuilder sb, TestModule module) {
        sb.append("    {\n");
        sb.append("      \"moduleId\": ").append(quote(module.moduleId())).append(",\n");
        sb.append("      \"tests\": ");
        appendTests(sb, module.tests());
        sb.append("\n    }");
    }

    private void appendTests(StringBuilder sb, List<TestCase> tests) {
        if (tests.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < tests.size(); i++) {
            appendTestCase(sb, tests.get(i));
            if (i < tests.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("      ]");
    }

    private void appendTestCase(StringBuilder sb, TestCase testCase) {
        sb.append("        {\n");
        sb.append("          \"name\": ").append(quote(testCase.name())).append(",\n");
        sb.append("          \"fullName\": ").append(quote(testCase.fullName())).append(",\n");
        sb.append("          \"state\": ").append(quote(testCase.state().jsonValue()));
        if (testCase.errors() != null) {
            sb.append(",\n          \"errors\": ");
            appendErrors(sb, testCase.errors());
        }
        sb.append("\n        }");
    }

    private void appendErrors(StringBuilder sb, List<TestError> errors) {
        if (errors.isEmpty()) {
            sb.append("[]");
            return;
        }
        sb.append("[\n");
        for (int i = 0; i < errors.size(); i++) {
            appendError(sb, errors.get(i));
            if (i < errors.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("          ]");
    }

    private void appendError(StringBuilder sb, TestError error) {
        sb.append("            {\n");
        sb.append("              \"message\": ").append(quote(error.message()));
        if (error.stack() != null) {
            sb.append(",\n              \"stack\": ").append(quote(error.stack()));
        }
        sb.append("\n            }");
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
