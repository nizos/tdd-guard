package com.lightspeed.tddguard.junit5.patterns;

import com.lightspeed.tddguard.junit5.SourceDirectoryResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SourceAnalyzer.
 * Validates test file discovery.
 */
class SourceAnalyzerTest {

    @Test
    void shouldFindJavaTestFiles(@TempDir Path projectRoot) throws IOException {
        // Given
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path testDir = projectRoot.resolve("src/test/java/com/example");
        Files.createDirectories(testDir);

        Path testFile1 = testDir.resolve("FooTest.java");
        Path testFile2 = testDir.resolve("BarTest.java");
        Files.writeString(testFile1, "public class FooTest {}");
        Files.writeString(testFile2, "public class BarTest {}");

        // Create a non-test file
        Path mainDir = projectRoot.resolve("src/main/java/com/example");
        Files.createDirectories(mainDir);
        Files.writeString(mainDir.resolve("Foo.java"), "public class Foo {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(2, testFiles.size());
        assertTrue(testFiles.contains(testFile1));
        assertTrue(testFiles.contains(testFile2));
    }

    @Test
    void shouldExcludeMainSourceFiles(@TempDir Path projectRoot) throws IOException {
        // Given
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path mainDir = projectRoot.resolve("src/main/java");
        Files.createDirectories(mainDir);
        Files.writeString(mainDir.resolve("Main.java"), "public class Main {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertTrue(testFiles.isEmpty());
    }

    @Test
    void shouldHandleNestedTestDirectories(@TempDir Path projectRoot) throws IOException {
        // Given
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path nestedTest = projectRoot.resolve("src/test/java/com/example/unit/api");
        Files.createDirectories(nestedTest);
        Path testFile = nestedTest.resolve("ApiTest.java");
        Files.writeString(testFile, "public class ApiTest {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(1, testFiles.size());
        assertEquals(testFile, testFiles.get(0));
    }

    @Test
    void shouldHandleEmptyProject(@TempDir Path projectRoot) {
        // Given
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertTrue(testFiles.isEmpty());
    }

    @Test
    void shouldFindKotlinTestFiles(@TempDir Path projectRoot) throws IOException {
        // Given
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path kotlinTestDir = projectRoot.resolve("src/test/kotlin/com/example");
        Files.createDirectories(kotlinTestDir);
        Path kotlinTest = kotlinTestDir.resolve("TestSpec.kt");
        Files.writeString(kotlinTest, "class TestSpec");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(1, testFiles.size());
        assertTrue(testFiles.get(0).toString().endsWith(".kt"));
    }

    @Test
    void shouldExcludeBuildDirectories(@TempDir Path projectRoot) throws IOException {
        // Given
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path buildTest = projectRoot.resolve("build/test-classes/com/example");
        Files.createDirectories(buildTest);
        Files.writeString(buildTest.resolve("Test.java"), "public class Test {}");

        Path realTest = projectRoot.resolve("src/test/java/com/example");
        Files.createDirectories(realTest);
        Path realTestFile = realTest.resolve("RealTest.java");
        Files.writeString(realTestFile, "public class RealTest {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(1, testFiles.size());
        assertEquals(realTestFile, testFiles.get(0));
    }

    @Test
    void shouldFindTestFilesInCustomTestDirectory(@TempDir Path projectRoot) throws IOException {
        // Given - custom test directory configured
        SourceDirectoryResolver resolver = createResolverWithTestDirs(projectRoot, "tests/unit");
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path customTestDir = projectRoot.resolve("tests/unit/com/example");
        Files.createDirectories(customTestDir);
        Path testFile = customTestDir.resolve("CustomTest.java");
        Files.writeString(testFile, "public class CustomTest {}");

        // Standard test directory should be ignored
        Path standardTest = projectRoot.resolve("src/test/java");
        Files.createDirectories(standardTest);
        Files.writeString(standardTest.resolve("StandardTest.java"), "public class StandardTest {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(1, testFiles.size());
        assertEquals(testFile, testFiles.get(0));
    }

    @Test
    void shouldFindTestFilesInMultipleConfiguredDirectories(@TempDir Path projectRoot) throws IOException {
        // Given - multiple custom test directories
        SourceDirectoryResolver resolver = createResolverWithTestDirs(projectRoot, "tests/unit,tests/integration");
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path unitDir = projectRoot.resolve("tests/unit");
        Files.createDirectories(unitDir);
        Path unitTest = unitDir.resolve("UnitTest.java");
        Files.writeString(unitTest, "public class UnitTest {}");

        Path integrationDir = projectRoot.resolve("tests/integration");
        Files.createDirectories(integrationDir);
        Path integrationTest = integrationDir.resolve("IntegrationTest.java");
        Files.writeString(integrationTest, "public class IntegrationTest {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(2, testFiles.size());
        assertTrue(testFiles.contains(unitTest));
        assertTrue(testFiles.contains(integrationTest));
    }

    @Test
    void shouldUseDefaultTestDirectoriesWhenNotConfigured(@TempDir Path projectRoot) throws IOException {
        // Given - no custom directories configured
        SourceDirectoryResolver resolver = createResolverWithDefaults(projectRoot);
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        // Create files in all three default directories
        Path javaTest = projectRoot.resolve("src/test/java/FooTest.java");
        Files.createDirectories(javaTest.getParent());
        Files.writeString(javaTest, "public class FooTest {}");

        Path kotlinTest = projectRoot.resolve("src/test/kotlin/BarTest.kt");
        Files.createDirectories(kotlinTest.getParent());
        Files.writeString(kotlinTest, "class BarTest");

        Path genericTest = projectRoot.resolve("src/test/BazTest.java");
        Files.createDirectories(genericTest.getParent());
        Files.writeString(genericTest, "public class BazTest {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(3, testFiles.size());
        assertTrue(testFiles.contains(javaTest));
        assertTrue(testFiles.contains(kotlinTest));
        assertTrue(testFiles.contains(genericTest));
    }

    @Test
    void shouldHandleNestedCustomTestDirectories(@TempDir Path projectRoot) throws IOException {
        // Given - custom test directory with deep nesting
        SourceDirectoryResolver resolver = createResolverWithTestDirs(projectRoot, "custom/test/path");
        SourceAnalyzer analyzer = new SourceAnalyzer(resolver);

        Path nestedDir = projectRoot.resolve("custom/test/path/com/example/unit");
        Files.createDirectories(nestedDir);
        Path testFile = nestedDir.resolve("NestedTest.java");
        Files.writeString(testFile, "public class NestedTest {}");

        // When
        List<Path> testFiles = analyzer.findTestFiles(projectRoot);

        // Then
        assertEquals(1, testFiles.size());
        assertEquals(testFile, testFiles.get(0));
    }

    // Helper methods

    private SourceDirectoryResolver createResolverWithDefaults(Path projectRoot) {
        return new SourceDirectoryResolver(
            projectRoot,
            key -> null,  // No system properties
            key -> null   // No environment variables
        );
    }

    private SourceDirectoryResolver createResolverWithTestDirs(Path projectRoot, String testDirs) {
        return new SourceDirectoryResolver(
            projectRoot,
            key -> key.equals("tddguard.testSourceDirs") ? testDirs : null,
            key -> null
        );
    }
}
