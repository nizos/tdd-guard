<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Tests;

use PHPUnit\Framework\TestCase;
use Symfony\Component\Filesystem\Filesystem;
use TddGuard\PHPUnit\Storage;

final class TddGuardStorageLocationTest extends TestCase
{
    private string $tempDir;
    private Filesystem $filesystem;
    private string $originalCwd;

    protected function setUp(): void
    {
        $this->filesystem = new Filesystem();
        $this->tempDir = sys_get_temp_dir() . '/tdd-guard-test-' . uniqid();
        $this->filesystem->mkdir($this->tempDir);
        $this->originalCwd = getcwd();
        chdir($this->tempDir);
    }

    protected function tearDown(): void
    {
        chdir($this->originalCwd);
        $this->filesystem->remove($this->tempDir);
    }

    public function testStorageSavesToCorrectLocation(): void
    {
        // Given: A storage configured with a specific project root
        $storage = new Storage($this->tempDir);

        // When: We save test results
        $testData = '{"testModules": []}';
        $storage->saveTest($testData);

        // Then: The file should be saved in the correct TDD Guard location
        $expectedPath = $this->tempDir . '/.claude/tdd-guard/data/test.json';
        $this->assertFileExists($expectedPath);
        $this->assertEquals($testData, file_get_contents($expectedPath));
    }

    public function testStorageRespectsEnvironmentVariable(): void
    {
        // Given: Environment variable is set
        $originalEnv = getenv('TDD_GUARD_PROJECT_ROOT');
        putenv('TDD_GUARD_PROJECT_ROOT=' . $this->tempDir);

        try {
            // When: Storage is created without explicit project root
            $storage = new Storage('');
            $testData = '{"testModules": []}';
            $storage->saveTest($testData);

            // Then: It should use the environment variable location
            $expectedPath = $this->tempDir . '/.claude/tdd-guard/data/test.json';
            $this->assertFileExists($expectedPath);

        } finally {
            if ($originalEnv !== false) {
                putenv('TDD_GUARD_PROJECT_ROOT=' . $originalEnv);
            } else {
                putenv('TDD_GUARD_PROJECT_ROOT');
            }
        }
    }
}
