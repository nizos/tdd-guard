package com.lightspeed.tddguard.junit5;

import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.TestExecutionListener;

import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for service provider auto-discovery.
 *
 * Covers AC#1: Service Provider Configuration
 */
class ServiceProviderTest {

    @Test
    void shouldDiscoverTddGuardListenerViaServiceLoader() {
        // Given: ServiceLoader for TestExecutionListener
        ServiceLoader<TestExecutionListener> loader = ServiceLoader.load(TestExecutionListener.class);

        // When: Loading all providers
        // Then: Should find TddGuardListener
        boolean found = StreamSupport.stream(loader.spliterator(), false)
            .anyMatch(listener -> listener instanceof TddGuardListener);

        assertTrue(found, "TddGuardListener should be auto-discovered via ServiceLoader");
    }

    @Test
    void shouldLoadWithoutManualConfiguration() {
        // Given: No manual configuration
        // When: Using ServiceLoader
        ServiceLoader<TestExecutionListener> loader = ServiceLoader.load(TestExecutionListener.class);

        // Then: Should load successfully and create instance
        TddGuardListener listener = StreamSupport.stream(loader.spliterator(), false)
            .filter(l -> l instanceof TddGuardListener)
            .map(l -> (TddGuardListener) l)
            .findFirst()
            .orElse(null);

        assertNotNull(listener, "Should create TddGuardListener instance via ServiceLoader");
    }

    @Test
    void shouldHaveCorrectServiceProviderFile() {
        // Given: ServiceLoader configuration
        // When: Loading service provider file
        // Then: Should be registered in META-INF/services
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        var resource = classLoader.getResource("META-INF/services/org.junit.platform.launcher.TestExecutionListener");

        assertNotNull(resource, "Service provider file should exist");
    }
}
