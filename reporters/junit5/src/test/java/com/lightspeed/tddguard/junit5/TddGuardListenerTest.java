package com.lightspeed.tddguard.junit5;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestIdentifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TddGuardListener - the main TestExecutionListener implementation.
 *
 * Covers AC#2: Self-Detection Logic
 */
class TddGuardListenerTest {

    @Test
    void shouldBeEnabledWhenEnvironmentVariableIsTrue(@TempDir Path tempDir) {
        // Given: TDDGUARD_ENABLED environment variable is set to true
        // When: Creating a TddGuardListener with project root
        // Then: Listener should be enabled

        // This test will fail until we implement the constructor
        TddGuardListener listener = new TddGuardListener(tempDir, () -> "true", () -> null);
        assertTrue(listener.isEnabled(), "Listener should be enabled when TDDGUARD_ENABLED=true");
    }

    @Test
    void shouldBeEnabledWhenTddGuardDirectoryExists(@TempDir Path tempDir) throws IOException {
        // Given: .claude/tdd-guard/ directory exists in project root
        Path tddGuardDir = tempDir.resolve(".claude/tdd-guard");
        Files.createDirectories(tddGuardDir);

        // When: Creating a TddGuardListener
        // Then: Listener should be enabled
        TddGuardListener listener = new TddGuardListener(tempDir, () -> null, () -> null);
        assertTrue(listener.isEnabled(), "Listener should be enabled when .claude/tdd-guard/ exists");
    }

    @Test
    void shouldBeDisabledWhenNeitherConditionMet(@TempDir Path tempDir) {
        // Given: No environment variable and no .claude/tdd-guard/ directory
        // When: Creating a TddGuardListener
        // Then: Listener should be disabled
        TddGuardListener listener = new TddGuardListener(tempDir, () -> null, () -> null);
        assertFalse(listener.isEnabled(), "Listener should be disabled when conditions not met");
    }

    @Test
    void shouldIgnoreEnvironmentVariableWithNonTrueValue(@TempDir Path tempDir) {
        // Given: TDDGUARD_ENABLED is set but not to "true"
        // When: Creating a TddGuardListener
        // Then: Listener should be disabled
        TddGuardListener listener = new TddGuardListener(tempDir, () -> "false", () -> null);
        assertFalse(listener.isEnabled(), "Listener should be disabled when TDDGUARD_ENABLED=false");

        listener = new TddGuardListener(tempDir, () -> "yes", () -> null);
        assertFalse(listener.isEnabled(), "Listener should be disabled when TDDGUARD_ENABLED=yes");
    }

    @Test
    void shouldBeCaseInsensitiveForEnvironmentVariable(@TempDir Path tempDir) {
        // Given: TDDGUARD_ENABLED is set to "True", "TRUE", etc.
        // When: Creating a TddGuardListener
        // Then: Listener should be enabled
        TddGuardListener listener = new TddGuardListener(tempDir, () -> "TRUE", () -> null);
        assertTrue(listener.isEnabled(), "Listener should accept TRUE");

        listener = new TddGuardListener(tempDir, () -> "True", () -> null);
        assertTrue(listener.isEnabled(), "Listener should accept True");
    }

    @Test
    void shouldReturnImmediatelyWhenDisabled(@TempDir Path tempDir) {
        // Given: Listener is disabled
        TddGuardListener listener = new TddGuardListener(tempDir, () -> null, () -> null);
        assertFalse(listener.isEnabled());

        // When: Calling lifecycle methods with null (listener should handle gracefully)
        // Then: Should return immediately without errors
        assertDoesNotThrow(() -> {
            listener.executionStarted(null);
            listener.executionFinished(null, TestExecutionResult.successful());
            listener.executionSkipped(null, "reason");
        });
    }
}
