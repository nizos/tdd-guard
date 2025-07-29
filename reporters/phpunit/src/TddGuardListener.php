<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit;

use PHPUnit\Framework\AssertionFailedError;
use PHPUnit\Framework\Test;
use PHPUnit\Framework\TestCase;
use PHPUnit\Framework\TestListener;
use PHPUnit\Framework\TestListenerDefaultImplementation;
use PHPUnit\Framework\TestSuite;
use PHPUnit\Framework\Warning;
use Throwable;

/**
 * @psalm-api
 */
final class TddGuardListener implements TestListener
{
    use TestListenerDefaultImplementation;

    private array $testResults = [];
    private string $projectRoot;

    public function __construct(string $projectRoot = '')
    {
        $this->projectRoot = PathValidator::resolveProjectRoot($projectRoot);
    }

    public function startTest(Test $test): void
    {
    }

    public function endTest(Test $test, float $time): void
    {
        if (!$test instanceof TestCase) {
            return;
        }

        $fullName = $test->toString();

        foreach ($this->testResults as $result) {
            if ($result['test']['fullName'] === $fullName) {
                return;
            }
        }

        $this->addTestResult($test, 'passed');
    }

    public function addError(Test $test, Throwable $t, float $time): void
    {
        $this->addTestResult($test, 'failed', $t->getMessage());
    }

    public function addFailure(Test $test, AssertionFailedError $e, float $time): void
    {
        $this->addTestResult($test, 'failed', $e->getMessage());
    }

    public function addWarning(Test $test, Warning $e, float $time): void
    {
        $this->addTestResult($test, 'failed', 'Warning: ' . $e->getMessage());
    }

    public function addIncompleteTest(Test $test, Throwable $t, float $time): void
    {
        $this->addTestResult($test, 'skipped', 'Incomplete: ' . $t->getMessage());
    }

    public function addRiskyTest(Test $test, Throwable $t, float $time): void
    {
        $this->addTestResult($test, 'failed', 'Risky: ' . $t->getMessage());
    }

    public function addSkippedTest(Test $test, Throwable $t, float $time): void
    {
        $this->addTestResult($test, 'skipped', $t->getMessage());
    }

    private function addTestResult(Test $test, string $state, ?string $message = null): void
    {
        if (!$test instanceof TestCase) {
            return;
        }

        $testName = $test->getName();
        $className = get_class($test);
        $fullName = $test->toString();

        $result = [
            'name' => $testName,
            'fullName' => $fullName,
            'state' => $state,
        ];

        if ($message !== null && $state === 'failed') {
            $result['errors'] = [
                ['message' => $message],
            ];
        }

        try {
            $reflection = new \ReflectionClass($className);
            $moduleId = $this->getRelativePath($reflection->getFileName());
        } catch (\ReflectionException $e) {
            $moduleId = str_replace('\\', '/', $className) . '.php';
        }

        $this->testResults[] = [
            'test' => $result,
            'module' => $moduleId,
        ];
    }

    private function getRelativePath(string $absolutePath): string
    {
        if (str_starts_with($absolutePath, $this->projectRoot)) {
            $relativePath = substr($absolutePath, strlen($this->projectRoot) + 1);

            return str_replace(DIRECTORY_SEPARATOR, '/', $relativePath);
        }

        return basename($absolutePath);
    }

    public function endTestSuite(TestSuite $suite): void
    {
        if (empty($this->testResults)) {
            return;
        }

        $this->saveResults();
    }

    private function saveResults(): void
    {
        $modules = [];
        foreach ($this->testResults as $item) {
            $moduleId = $item['module'];
            if (!isset($modules[$moduleId])) {
                $modules[$moduleId] = [
                    'moduleId' => $moduleId,
                    'tests' => [],
                ];
            }
            $modules[$moduleId]['tests'][] = $item['test'];
        }

        $output = [
            'testModules' => array_values($modules),
        ];

        $reporter = new Storage($this->projectRoot);
        $reporter->saveTest(json_encode($output, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
    }
}
