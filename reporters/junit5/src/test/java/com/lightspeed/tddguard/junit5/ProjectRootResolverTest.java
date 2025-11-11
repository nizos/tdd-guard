package com.lightspeed.tddguard.junit5;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ProjectRootResolver.
 *
 * Covers AC#3: Project Root Resolution
 */
class ProjectRootResolverTest {

    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUpStreams() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void restoreStreams() {
        System.setErr(originalErr);
    }

    @Test
    void shouldUseSystemPropertyWhenSet(@TempDir Path tempDir) {
        // Given: System property is set
        Path expected = tempDir.resolve("project");
        String sysPropValue = expected.toString();

        // When: Resolving project root
        ProjectRootResolver resolver = new ProjectRootResolver(() -> sysPropValue, () -> null, tempDir);
        Path actual = resolver.resolve();

        // Then: Should return system property value
        assertEquals(expected.toAbsolutePath(), actual);
    }

    @Test
    void shouldUseEnvironmentVariableWhenSystemPropertyNotSet(@TempDir Path tempDir) {
        // Given: System property not set, but environment variable is
        Path expected = tempDir.resolve("project-env");
        String envVarValue = expected.toString();

        // When: Resolving project root
        ProjectRootResolver resolver = new ProjectRootResolver(() -> null, () -> envVarValue, tempDir);
        Path actual = resolver.resolve();

        // Then: Should return environment variable value
        assertEquals(expected.toAbsolutePath(), actual);
    }

    @Test
    void shouldPreferSystemPropertyOverEnvironmentVariable(@TempDir Path tempDir) {
        // Given: Both system property and environment variable are set
        Path expectedFromSysProp = tempDir.resolve("from-sysprop");
        Path notExpectedFromEnv = tempDir.resolve("from-env");

        // When: Resolving project root
        ProjectRootResolver resolver = new ProjectRootResolver(
            () -> expectedFromSysProp.toString(),
            () -> notExpectedFromEnv.toString(),
            tempDir
        );
        Path actual = resolver.resolve();

        // Then: Should prefer system property
        assertEquals(expectedFromSysProp.toAbsolutePath(), actual);
    }

    @Test
    void shouldTraverseUpToFindBuildGradle(@TempDir Path tempDir) throws IOException {
        // Given: build.gradle exists in parent directory
        Path projectRoot = tempDir.resolve("project");
        Path subDir = projectRoot.resolve("sub1/sub2");
        Files.createDirectories(subDir);
        Files.createFile(projectRoot.resolve("build.gradle"));

        // When: Resolving from subdirectory
        ProjectRootResolver resolver = new ProjectRootResolver(() -> null, () -> null, subDir);
        Path actual = resolver.resolve();

        // Then: Should find project root with build.gradle
        assertEquals(projectRoot, actual);
    }

    @Test
    void shouldTraverseUpToFindBuildGradleKts(@TempDir Path tempDir) throws IOException {
        // Given: build.gradle.kts exists in parent directory
        Path projectRoot = tempDir.resolve("kotlin-project");
        Path subDir = projectRoot.resolve("module/src/main");
        Files.createDirectories(subDir);
        Files.createFile(projectRoot.resolve("build.gradle.kts"));

        // When: Resolving from subdirectory
        ProjectRootResolver resolver = new ProjectRootResolver(() -> null, () -> null, subDir);
        Path actual = resolver.resolve();

        // Then: Should find project root with build.gradle.kts
        assertEquals(projectRoot, actual);
    }

    @Test
    void shouldTraverseUpToFindPomXml(@TempDir Path tempDir) throws IOException {
        // Given: pom.xml exists in parent directory
        Path projectRoot = tempDir.resolve("maven-project");
        Path subDir = projectRoot.resolve("src/test/java");
        Files.createDirectories(subDir);
        Files.createFile(projectRoot.resolve("pom.xml"));

        // When: Resolving from subdirectory
        ProjectRootResolver resolver = new ProjectRootResolver(() -> null, () -> null, subDir);
        Path actual = resolver.resolve();

        // Then: Should find project root with pom.xml
        assertEquals(projectRoot, actual);
    }

    @Test
    void shouldFallbackToWorkingDirectoryWhenNoBuildFileFound(@TempDir Path tempDir) {
        // Given: No build files exist anywhere
        Path startingDir = tempDir.resolve("no-build-files");

        // When: Resolving project root
        ProjectRootResolver resolver = new ProjectRootResolver(() -> null, () -> null, startingDir);
        Path actual = resolver.resolve();

        // Then: Should return the starting directory
        assertEquals(startingDir.toAbsolutePath(), actual);

        // And: Should log a warning
        String errorOutput = errContent.toString();
        assertTrue(
            errorOutput.contains("TDD Guard: No build file found"),
            "Should log warning when falling back to working directory"
        );
        assertTrue(
            errorOutput.contains(startingDir.toAbsolutePath().toString()),
            "Warning should include the fallback directory path"
        );
    }

    @Test
    void shouldReturnAbsolutePaths(@TempDir Path tempDir) throws IOException {
        // Given: Relative path provided in system property
        Path projectRoot = tempDir.resolve("relative-project");
        Files.createDirectories(projectRoot);

        // When: Resolving with relative path
        ProjectRootResolver resolver = new ProjectRootResolver(
            () -> ".",
            () -> null,
            tempDir
        );
        Path actual = resolver.resolve();

        // Then: Should return absolute path
        assertTrue(actual.isAbsolute(), "Resolved path should be absolute");
    }
}
