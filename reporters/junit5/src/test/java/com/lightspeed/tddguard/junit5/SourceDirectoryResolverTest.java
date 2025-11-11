package com.lightspeed.tddguard.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SourceDirectoryResolver.
 * Validates dynamic source directory discovery with multiple fallback strategies.
 */
class SourceDirectoryResolverTest {

    @Test
    void shouldResolveTestDirsFromSystemProperty(@TempDir Path projectRoot) {
        // Given: System property specifies custom test source directories
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "custom/test/java,custom/test/kotlin";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("custom/test/java", "custom/test/kotlin"), testDirs);
    }

    @Test
    void shouldReturnEmptyListWhenPropertyIsNull(@TempDir Path projectRoot) {
        // Given: System property returns null
        Function<String, String> sysPropSupplier = key -> null;
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertTrue(testDirs.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenPropertyIsEmpty(@TempDir Path projectRoot) {
        // Given: System property returns empty string
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertTrue(testDirs.isEmpty());
    }

    @Test
    void shouldReturnEmptyListWhenPropertyIsWhitespace(@TempDir Path projectRoot) {
        // Given: System property returns only whitespace
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "   ";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertTrue(testDirs.isEmpty());
    }

    @Test
    void shouldHandleSingleDirectory(@TempDir Path projectRoot) {
        // Given: System property contains single directory
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "src/test/java";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("src/test/java"), testDirs);
    }

    @Test
    void shouldFilterOutEmptyElementsFromMultipleCommas(@TempDir Path projectRoot) {
        // Given: System property contains multiple commas creating empty elements
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "src/test/java,,src/test/kotlin";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("src/test/java", "src/test/kotlin"), testDirs);
    }

    @Test
    void shouldTrimWhitespaceAroundDirectories(@TempDir Path projectRoot) {
        // Given: System property contains directories with surrounding whitespace
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return " src/test/java , src/test/kotlin ";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("src/test/java", "src/test/kotlin"), testDirs);
    }

    @Test
    void shouldHandleTrailingComma(@TempDir Path projectRoot) {
        // Given: System property contains trailing comma
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "src/test/java,";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("src/test/java"), testDirs);
    }

    @Test
    void shouldHandleLeadingComma(@TempDir Path projectRoot) {
        // Given: System property contains leading comma
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return ",src/test/java";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("src/test/java"), testDirs);
    }

    @Test
    void shouldFallbackToEnvVarWhenSystemPropertyMissing(@TempDir Path projectRoot) {
        // Given: System property is null, but environment variable has value
        Function<String, String> sysPropSupplier = key -> null;
        Function<String, String> envVarSupplier = key -> {
            if ("TDDGUARD_TEST_SOURCE_DIRS".equals(key)) {
                return "env/test/java,env/test/kotlin";
            }
            return null;
        };

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then
        assertEquals(Arrays.asList("env/test/java", "env/test/kotlin"), testDirs);
    }

    @Test
    void shouldPreferSystemPropertyOverEnvVar(@TempDir Path projectRoot) {
        // Given: Both system property and environment variable have values
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.testSourceDirs".equals(key)) {
                return "sysprop/test";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> {
            if ("TDDGUARD_TEST_SOURCE_DIRS".equals(key)) {
                return "env/test";
            }
            return null;
        };

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> testDirs = resolver.resolveTestSourceDirs();

        // Then: System property takes precedence
        assertEquals(java.util.Collections.singletonList("sysprop/test"), testDirs);
    }

    @Test
    void shouldResolveMainDirsFromSystemProperty(@TempDir Path projectRoot) {
        // Given: System property specifies custom main source directories
        Function<String, String> sysPropSupplier = key -> {
            if ("tddguard.mainSourceDirs".equals(key)) {
                return "custom/main/java,custom/main/kotlin";
            }
            return null;
        };
        Function<String, String> envVarSupplier = key -> null;

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> mainDirs = resolver.resolveMainSourceDirs();

        // Then
        assertEquals(Arrays.asList("custom/main/java", "custom/main/kotlin"), mainDirs);
    }

    @Test
    void shouldHandleMainDirsWithEnvVarFallback(@TempDir Path projectRoot) {
        // Given: System property is null, but environment variable has value for main dirs
        Function<String, String> sysPropSupplier = key -> null;
        Function<String, String> envVarSupplier = key -> {
            if ("TDDGUARD_MAIN_SOURCE_DIRS".equals(key)) {
                return "env/main/java";
            }
            return null;
        };

        // When
        SourceDirectoryResolver resolver = new SourceDirectoryResolver(
            projectRoot, sysPropSupplier, envVarSupplier
        );
        List<String> mainDirs = resolver.resolveMainSourceDirs();

        // Then
        assertEquals(java.util.Collections.singletonList("env/main/java"), mainDirs);
    }
}
