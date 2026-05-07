package io.github.nizos.tddguard.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CompilationErrorHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsClassNameFromSourceFileAsModuleId() throws IOException {
        File sourceFile = createSourceFile("MyTest.java", "class MyTest {}");

        String moduleId = CompilationErrorHandler.extractModuleId(Set.of(sourceFile));

        assertEquals("MyTest", moduleId);
    }

    @Test
    void fallsBackToCompilationErrorWhenNoSourceFiles() {
        String moduleId = CompilationErrorHandler.extractModuleId(Set.of());

        assertEquals("CompilationError", moduleId);
    }

    @Test
    void usesFirstSourceFileWhenMultipleFilesPresent() throws IOException {
        File fileA = createSourceFile("ATest.java", "class ATest {}");
        File fileB = createSourceFile("BTest.java", "class BTest {}");
        Set<File> sources = Set.of(fileA, fileB);

        String moduleId = CompilationErrorHandler.extractModuleId(sources);

        // Should be one of the class names
        assertTrue(moduleId.equals("ATest") || moduleId.equals("BTest"));
    }

    @Test
    void buildMessageUsesCapturedOutputWhenAvailable() {
        String captured = "error: package com.example.missing does not exist\nimport com.example.missing.Foo;\n";

        String message = CompilationErrorHandler.buildMessage(captured, Set.of());

        assertTrue(message.contains("com.example.missing"));
    }

    @Test
    void buildMessageFallsBackToImportParsingWhenOutputEmpty() throws IOException {
        File sourceFile = createSourceFile("MyTest.java",
                "import com.nonexistent.module.Foo;\nimport org.junit.jupiter.api.Test;\nclass MyTest {}");

        String message = CompilationErrorHandler.buildMessage("", Set.of(sourceFile));

        assertTrue(message.contains("com.nonexistent.module"));
    }

    @Test
    void buildMessageIncludesSourceFileNameWhenOutputEmpty() throws IOException {
        File sourceFile = createSourceFile("SingleImportErrorTest.java",
                "import com.nonexistent.module.Foo;\nclass SingleImportErrorTest {}");

        String message = CompilationErrorHandler.buildMessage("", Set.of(sourceFile));

        assertTrue(message.contains("SingleImportErrorTest"));
    }

    @Test
    void handleWritesSyntheticTestJson() throws IOException {
        File sourceFile = createSourceFile("SingleImportErrorTest.java",
                "import com.nonexistent.module.NonExistentClass;\nclass SingleImportErrorTest {}");
        Path outputDir = tempDir.resolve("output");
        String captured = "error: package com.nonexistent.module does not exist";

        CompilationErrorHandler.handle(outputDir, captured, Set.of(sourceFile));

        Path testJson = outputDir.resolve("test.json");
        assertTrue(Files.exists(testJson));
        String content = Files.readString(testJson);
        assertTrue(content.contains("SingleImportErrorTest"));
        assertTrue(content.contains("CompilationError"));
        assertTrue(content.contains("failed"));
    }

    @Test
    void handleWritesValidSchemaStructure() throws IOException {
        File sourceFile = createSourceFile("MyTest.java", "class MyTest {}");
        Path outputDir = tempDir.resolve("output");

        CompilationErrorHandler.handle(outputDir, "error: something went wrong", Set.of(sourceFile));

        String content = Files.readString(outputDir.resolve("test.json"));
        assertTrue(content.contains("\"testModules\""));
        assertTrue(content.contains("\"reason\""));
        assertTrue(content.contains("\"failed\""));
        assertTrue(content.contains("\"state\""));
    }

    // helpers

    private File createSourceFile(String name, String content) throws IOException {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file.toFile();
    }
}
