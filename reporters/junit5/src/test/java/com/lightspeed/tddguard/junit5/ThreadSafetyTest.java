package com.lightspeed.tddguard.junit5;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestIdentifier;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Thread safety tests for TestResultCollector.
 *
 * Covers: MEDIUM PRIORITY FIX - Thread Safety Issue
 * JUnit Platform can execute tests in parallel, so TestResultCollector must be thread-safe.
 */
class ThreadSafetyTest {

    @RepeatedTest(10) // Repeat to increase chance of catching race conditions
    void shouldHandleConcurrentTestRecording(@TempDir Path tempDir) throws Exception {
        // Given: TestResultCollector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver(tempDir);
        TestResultCollector collector = new TestResultCollector(tempDir, resolver);
        collector.testPlanStarted();

        int numThreads = 10;
        int testsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<Exception> exceptions = new ArrayList<>();

        // When: Multiple threads record test results concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready

                    for (int j = 0; j < testsPerThread; j++) {
                        TestIdentifier testId = createTestIdentifier("thread" + threadId + "_test" + j);
                        collector.recordTestStart(testId);
                        collector.recordTestFinish(testId, TestExecutionResult.successful());
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        // Then: No exceptions should occur
        if (!exceptions.isEmpty()) {
            fail("Thread safety violation detected: " + exceptions.get(0).getMessage());
        }

        // And: All tests should be recorded
        assertEquals(
            numThreads * testsPerThread,
            collector.getTotalTests(),
            "All tests should be recorded without loss"
        );
    }

    @RepeatedTest(10)
    void shouldHandleConcurrentFailureRecording(@TempDir Path tempDir) throws Exception {
        // Given: TestResultCollector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver(tempDir);
        TestResultCollector collector = new TestResultCollector(tempDir, resolver);
        collector.testPlanStarted();

        int numThreads = 10;
        int testsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<Exception> exceptions = new ArrayList<>();

        // When: Multiple threads record failures concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    for (int j = 0; j < testsPerThread; j++) {
                        TestIdentifier testId = createTestIdentifier("thread" + threadId + "_test" + j);
                        collector.recordTestStart(testId);
                        TestExecutionResult result = TestExecutionResult.failed(
                            new AssertionError("Test failed in thread " + threadId)
                        );
                        collector.recordTestFinish(testId, result);
                    }
                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        // Then: No exceptions should occur
        if (!exceptions.isEmpty()) {
            fail("Thread safety violation detected: " + exceptions.get(0).getMessage());
        }

        // And: All failures should be recorded
        assertEquals(
            numThreads * testsPerThread,
            collector.getFailedTests(),
            "All failures should be recorded without loss"
        );
    }

    @Test
    void shouldHandleMixedConcurrentOperations(@TempDir Path tempDir) throws Exception {
        // Given: TestResultCollector with default resolver
        SourceDirectoryResolver resolver = createDefaultResolver(tempDir);
        TestResultCollector collector = new TestResultCollector(tempDir, resolver);
        collector.testPlanStarted();

        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numThreads);
        List<Exception> exceptions = new ArrayList<>();

        // When: Multiple threads perform different operations concurrently
        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // Mix of operations
                    for (int j = 0; j < 50; j++) {
                        TestIdentifier testId = createTestIdentifier("thread" + threadId + "_test" + j);

                        if (j % 3 == 0) {
                            // Passed test
                            collector.recordTestStart(testId);
                            collector.recordTestFinish(testId, TestExecutionResult.successful());
                        } else if (j % 3 == 1) {
                            // Failed test
                            collector.recordTestStart(testId);
                            collector.recordTestFinish(testId, TestExecutionResult.failed(new AssertionError()));
                        } else {
                            // Skipped test
                            collector.recordSkipped(testId, "Skipped");
                        }
                    }

                    // Also read while writing
                    collector.getTotalTests();
                    collector.getPassedTests();
                    collector.getFailedTests();
                    collector.getSkippedTests();

                } catch (Exception e) {
                    synchronized (exceptions) {
                        exceptions.add(e);
                    }
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
        executor.shutdown();

        // Then: No exceptions should occur
        if (!exceptions.isEmpty()) {
            fail("Thread safety violation detected: " + exceptions.get(0).getMessage());
        }

        // And: Counts should be consistent
        int total = collector.getTotalTests();
        int passed = collector.getPassedTests();
        int failed = collector.getFailedTests();
        int skipped = collector.getSkippedTests();

        assertEquals(total, passed + failed + skipped, "Counts should be consistent");
    }

    private TestIdentifier createTestIdentifier(String uniqueId) {
        MethodSource source = MethodSource.from("ThreadSafetyTest", uniqueId);
        return TestIdentifier.from(new TestDescriptorStub(
            uniqueId,
            uniqueId,
            source
        ));
    }

    private SourceDirectoryResolver createDefaultResolver(Path projectRoot) {
        return new SourceDirectoryResolver(
            projectRoot,
            prop -> null,
            env -> null
        );
    }
}
