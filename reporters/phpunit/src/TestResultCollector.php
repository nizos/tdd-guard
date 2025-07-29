<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit;

final class TestResultCollector
{
    private array $testResults = [];
    private Storage $storage;
    private string $projectRoot;

    public function __construct(Storage $storage, string $projectRoot)
    {
        $this->storage = $storage;
        $this->projectRoot = rtrim($projectRoot, DIRECTORY_SEPARATOR);
    }

    public function addTestResult($test, string $state, ?string $message = null): void
    {
        $testName = $test->name();
        $className = $test->className();
        $fullName = $className . '::' . $testName;

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
            $moduleId = str_replace('\\', '/', (string) $className) . '.php';
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

        $cwd = getcwd();
        if (str_starts_with($absolutePath, $cwd)) {
            $relativePath = substr($absolutePath, strlen($cwd) + 1);

            return str_replace(DIRECTORY_SEPARATOR, '/', $relativePath);
        }

        return basename($absolutePath);
    }

    public function saveResults(): void
    {
        if (empty($this->testResults)) {
            return;
        }

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

        $this->storage->saveTest(json_encode($output, JSON_PRETTY_PRINT | JSON_UNESCAPED_SLASHES));
    }
}
