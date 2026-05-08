package io.github.nizos.tddguard.junit5;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TddGuardPluginTest {

    @Test
    void handlesCompileJavaFailures() {
        assertTrue(TddGuardPlugin.COMPILATION_TASKS.contains("compileJava"),
                "Plugin must handle compileJava so production source failures produce a test.json");
    }

    @Test
    void handlesCompileTestJavaFailures() {
        assertTrue(TddGuardPlugin.COMPILATION_TASKS.contains("compileTestJava"),
                "Plugin must handle compileTestJava so test source failures produce a test.json");
    }
}
