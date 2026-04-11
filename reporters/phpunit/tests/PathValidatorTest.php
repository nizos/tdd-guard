<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Tests;

use PHPUnit\Framework\TestCase;
use Symfony\Component\Filesystem\Filesystem;
use TddGuard\PHPUnit\PathValidator;

final class PathValidatorTest extends TestCase
{
    private string $tempDir;
    private Filesystem $filesystem;
    private string $originalCwd;

    protected function setUp(): void
    {
        $this->filesystem = new Filesystem();
        $this->tempDir = sys_get_temp_dir() . '/tdd-guard-path-test-' . uniqid();
        $this->filesystem->mkdir($this->tempDir);
        $this->originalCwd = getcwd();
    }

    protected function tearDown(): void
    {
        chdir($this->originalCwd);
        $this->filesystem->remove($this->tempDir);
    }

    public function testAllowsAncestorOfCurrentDirectory(): void
    {
        // Given: Working in a subdirectory
        $subDir = $this->tempDir . '/subdir';
        $this->filesystem->mkdir($subDir);
        chdir($subDir);

        // When: Validating the parent directory
        $result = PathValidator::resolveProjectRoot($this->tempDir);

        // Then: Should return the validated path
        $this->assertEquals(realpath($this->tempDir), $result);
    }

    public function testAcceptsAbsolutePath(): void
    {
        // Given: Working in a directory
        chdir($this->tempDir);

        // When: Validating the current directory using its absolute path
        $result = PathValidator::resolveProjectRoot($this->tempDir);

        // Then: Should return the resolved absolute path
        $this->assertEquals(realpath($this->tempDir), $result);
    }

    public function testAcceptsRelativePath(): void
    {
        // Given: Working in a directory
        chdir($this->tempDir);

        // When: Validating the current directory using a relative path
        $result = PathValidator::resolveProjectRoot('.');

        // Then: Should resolve and return the absolute path
        $this->assertEquals(realpath($this->tempDir), $result);
    }

    public function testAcceptsPathContainingDotDot(): void
    {
        // Given: Working in a subdirectory
        $subDir = $this->tempDir . '/subdir';
        $this->filesystem->mkdir($subDir);
        chdir($subDir);

        // When: Validating a path that uses `..` to reach a valid ancestor
        $result = PathValidator::resolveProjectRoot($subDir . '/..');

        // Then: Should normalize and return the absolute path
        $this->assertEquals(realpath($this->tempDir), $result);
    }

    public function testRejectsNonAncestorDirectory(): void
    {
        // Given: Two sibling directories
        $dir1 = $this->tempDir . '/dir1';
        $dir2 = $this->tempDir . '/dir2';
        $this->filesystem->mkdir($dir1);
        $this->filesystem->mkdir($dir2);
        chdir($dir1);

        // Then: Should throw exception
        $this->expectException(\InvalidArgumentException::class);
        $this->expectExceptionMessage('Configured project root is invalid');

        // When: Trying to use sibling directory as project root
        PathValidator::resolveProjectRoot($dir2);
    }
}
