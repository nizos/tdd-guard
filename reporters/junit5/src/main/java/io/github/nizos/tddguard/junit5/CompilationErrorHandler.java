package io.github.nizos.tddguard.junit5;

import io.github.nizos.tddguard.junit5.model.TestCase;
import io.github.nizos.tddguard.junit5.model.TestError;
import io.github.nizos.tddguard.junit5.model.TestModule;
import io.github.nizos.tddguard.junit5.model.TestResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Synthesizes a {@code test.json} when compilation fails before
 * the JUnit Platform can start (and the SPI listener never fires).
 *
 * <p>Mirrors the "synthetic failed module" pattern used by the Go and Rust reporters.
 */
final class CompilationErrorHandler {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+(static\\s+)?([\\w.]+);", Pattern.MULTILINE);

    private CompilationErrorHandler() {}

    /**
     * Writes a synthetic {@code test.json} under {@code outputDir} representing
     * a compilation failure.
     */
    static void handle(Path outputDir, String capturedOutput, Set<File> sourceFiles) {
        String moduleId = extractModuleId(sourceFiles);
        String message = buildMessage(capturedOutput, sourceFiles);

        TestError error = new TestError(message);
        TestCase testCase = TestCase.failed(
                "CompilationError",
                moduleId + "::CompilationError",
                List.of(error)
        );
        TestModule module = new TestModule(moduleId, List.of(testCase));
        TestResult result = new TestResult(List.of(module), "failed");

        try {
            new TestJsonWriter().write(outputDir, result);
        } catch (IOException e) {
            System.err.println("[tdd-guard-junit5] failed to write compilation error result: " + e.getMessage());
        }
    }

    /**
     * Derives the moduleId from the alphabetically-first source file's class name
     * (filename without {@code .java} extension), or falls back to
     * {@code "CompilationError"} when no source files are available.
     *
     * <p>Design rationale:
     * <ul>
     *   <li>The Rust reporter uses a fixed {@code "compilation"} module id because
     *       Rust errors are reported per-crate. Java errors are per-file, so a class
     *       name gives the LLM a more actionable identifier.</li>
     *   <li>The Go reporter uses the package import path because Go compiles per-package.
     *       Java's natural unit is the class, so the class name is the analog.</li>
     *   <li>Alphabetical sort on a {@code Set<File>} (which has no inherent order)
     *       ensures deterministic output across JVM implementations.</li>
     * </ul>
     */
    static String extractModuleId(Set<File> sourceFiles) {
        return sourceFiles.stream()
                .filter(f -> f.getName().endsWith(".java"))
                .map(f -> {
                    String name = f.getName();
                    return name.substring(0, name.length() - 5);
                })
                .sorted()
                .findFirst()
                .orElse("CompilationError");
    }

    /**
     * Builds an error message from the captured compiler output when available,
     * or falls back to parsing import statements in the source files.
     */
    static String buildMessage(String capturedOutput, Set<File> sourceFiles) {
        if (capturedOutput != null && !capturedOutput.isBlank()) {
            String filtered = capturedOutput.lines()
                    .filter(line -> line.contains("error:") || line.contains("package")
                            || line.contains("cannot find"))
                    .collect(Collectors.joining("\n"))
                    .strip();
            if (!filtered.isEmpty()) {
                return filtered;
            }
        }
        return buildMessageFromSources(sourceFiles);
    }

    private static String buildMessageFromSources(Set<File> sourceFiles) {
        StringBuilder sb = new StringBuilder();

        for (File file : sourceFiles.stream().sorted().collect(Collectors.toList())) {
            if (!file.getName().endsWith(".java")) continue;
            String className = file.getName().replaceAll("\\.java$", "");

            try {
                String content = Files.readString(file.toPath());
                List<String> imports = extractImportPackages(content);
                if (!imports.isEmpty()) {
                    sb.append("Compilation failed in ").append(className)
                            .append(".java: error: cannot find symbol (check imports: ")
                            .append(String.join(", ", imports))
                            .append(")");
                } else {
                    sb.append("Compilation failed in ").append(className).append(".java");
                }
            } catch (IOException e) {
                sb.append("Compilation failed in ").append(className).append(".java");
            }

            sb.append("\n");
        }

        String result = sb.toString().strip();
        return result.isEmpty() ? "Compilation failed. Run the build for details." : result;
    }

    private static List<String> extractImportPackages(String sourceContent) {
        Matcher m = IMPORT_PATTERN.matcher(sourceContent);
        return m.results()
                .map(r -> r.group(2))
                .filter(pkg -> !pkg.startsWith("java.") && !pkg.startsWith("javax."))
                .collect(Collectors.toList());
    }
}
