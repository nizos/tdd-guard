package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestError;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the canonical TDD Guard {@code test.json} format produced by
 * {@link TestJsonWriter} back into a {@link TestResult}.
 *
 * <p>The format is deterministic and internally controlled, so a targeted
 * structural parser is sufficient. No external JSON library is required.
 */
final class TestJsonReader {


    TestResult parse(String json) {
        String reason = extractStringField(json, "reason");
        List<TestModule> modules = parseModules(json);
        return new TestResult(modules, reason);
    }

    private List<TestModule> parseModules(String json) {
        int arrayStart = findArrayStart(json, "testModules");
        if (arrayStart < 0) return List.of();
        String arrayContent = extractBalancedSection(json, arrayStart, '[', ']');
        List<TestModule> modules = new ArrayList<>();
        for (String block : splitTopLevelObjects(arrayContent)) {
            TestModule m = parseModule(block);
            if (m != null) modules.add(m);
        }
        return modules;
    }

    private TestModule parseModule(String json) {
        String moduleId = extractStringField(json, "moduleId");
        if (moduleId == null) return null;
        List<TestCase> tests = parseTests(json);
        return new TestModule(moduleId, tests);
    }

    private List<TestCase> parseTests(String json) {
        int arrayStart = findArrayStart(json, "tests");
        if (arrayStart < 0) return List.of();
        String arrayContent = extractBalancedSection(json, arrayStart, '[', ']');
        List<TestCase> tests = new ArrayList<>();
        for (String block : splitTopLevelObjects(arrayContent)) {
            TestCase tc = parseTestCase(block);
            if (tc != null) tests.add(tc);
        }
        return tests;
    }

    private TestCase parseTestCase(String json) {
        String name = extractStringField(json, "name");
        String fullName = extractStringField(json, "fullName");
        String stateStr = extractStringField(json, "state");
        if (name == null || fullName == null || stateStr == null) return null;

        List<TestError> errors = parseErrors(json);
        return switch (stateStr) {
            case "passed"  -> TestCase.passed(name, fullName);
            case "skipped" -> TestCase.skipped(name, fullName);
            default        -> TestCase.failed(name, fullName,
                                errors.isEmpty() ? List.of(new TestError("")) : errors);
        };
    }

    private List<TestError> parseErrors(String json) {
        int arrayStart = findArrayStart(json, "errors");
        if (arrayStart < 0) return List.of();
        String arrayContent = extractBalancedSection(json, arrayStart, '[', ']');
        List<TestError> errors = new ArrayList<>();
        for (String block : splitTopLevelObjects(arrayContent)) {
            String message = extractStringField(block, "message");
            String stack   = extractStringField(block, "stack");
            if (message != null) errors.add(new TestError(message, stack));
        }
        return errors;
    }

    /**
     * Finds the opening {@code [} of the named JSON array field.
     *
     * @return index of the {@code [}, or -1 if not found
     */
    private int findArrayStart(String json, String fieldName) {
        // Search for "fieldName": [
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\\[");
        Matcher m = p.matcher(json);
        if (!m.find()) return -1;
        return m.end() - 1; // index of '['
    }

    /**
     * Extracts the content between the matching open/close delimiters starting at
     * {@code startIndex} (which must be the position of the opening delimiter).
     * Correctly skips JSON string literals to handle delimiters inside strings.
     */
    private String extractBalancedSection(String json, int startIndex, char open, char close) {
        int depth = 0;
        int contentStart = startIndex + 1;
        boolean inString = false;
        for (int i = startIndex; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (c == '\\') i++; // skip escaped character
                else if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == open) depth++;
                else if (c == close) {
                    depth--;
                    if (depth == 0) return json.substring(contentStart, i);
                }
            }
        }
        return "";
    }

    /**
     * Splits a JSON array body into top-level {@code {...}} object strings.
     * Correctly handles nested objects and JSON strings.
     */
    private List<String> splitTopLevelObjects(String arrayBody) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        int objectStart = -1;
        boolean inString = false;
        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (inString) {
                if (c == '\\') i++;
                else if (c == '"') inString = false;
            } else {
                if (c == '"') inString = true;
                else if (c == '{') {
                    if (depth == 0) objectStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 0 && objectStart >= 0) {
                        objects.add(arrayBody.substring(objectStart, i + 1));
                        objectStart = -1;
                    }
                }
            }
        }
        return objects;
    }

    /**
     * Extracts the first occurrence of {@code "fieldName": "value"} and
     * returns the unescaped string value, or {@code null} if not found.
     */
    private String extractStringField(String json, String fieldName) {
        Pattern p = Pattern.compile(
                "\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    /** Unescapes a JSON string value. */
    private String unescape(String escaped) {
        // Process character by character to handle all escape sequences correctly
        StringBuilder sb = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '\\' && i + 1 < escaped.length()) {
                char next = escaped.charAt(++i);
                switch (next) {
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case 'n'  -> sb.append('\n');
                    case 'r'  -> sb.append('\r');
                    case 't'  -> sb.append('\t');
                    case 'b'  -> sb.append('\b');
                    case 'f'  -> sb.append('\f');
                    case 'u'  -> {
                        if (i + 4 < escaped.length()) {
                            String hex = escaped.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                        }
                    }
                    default   -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
