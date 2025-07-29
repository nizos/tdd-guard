<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Tests;

use PHPUnit\Framework\TestCase;
use Symfony\Component\Filesystem\Filesystem;

final class TddGuardExtensionTest extends TestCase
{
    private string $tempDir;
    private Filesystem $filesystem;

    protected function setUp(): void
    {
        $this->filesystem = new Filesystem();
        $this->tempDir = sys_get_temp_dir() . '/tdd-guard-test-' . uniqid();
        $this->filesystem->mkdir($this->tempDir);
    }

    protected function tearDown(): void
    {
        $this->filesystem->remove($this->tempDir);
    }

    public function testExtensionCapturesPassingTest(): void
    {
        // Given: A test file that will pass
        $testFile = $this->tempDir . '/ExampleTest.php';
        file_put_contents($testFile, '<?php
use PHPUnit\Framework\TestCase;
class ExampleTest extends TestCase {
    public function testPassing(): void {
        $this->assertTrue(true);
    }
}');

        $phpunitXml = $this->tempDir . '/phpunit.xml';
        file_put_contents($phpunitXml, '<?xml version="1.0" encoding="UTF-8"?>
<phpunit xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNamespaceSchemaLocation="vendor/phpunit/phpunit/phpunit.xsd"
         bootstrap="' . dirname(__DIR__) . '/vendor/autoload.php">
    <testsuites>
        <testsuite name="Example">
            <file>' . $testFile . '</file>
        </testsuite>
    </testsuites>
    <extensions>
        <bootstrap class="TddGuard\PHPUnit\TddGuardExtension">
            <parameter name="projectRoot" value="' . $this->tempDir . '"/>
        </bootstrap>
    </extensions>
</phpunit>');

        // When: We run PHPUnit with our extension
        $command = sprintf(
            'cd %s && php %s/vendor/bin/phpunit -c %s 2>&1',
            escapeshellarg($this->tempDir),
            escapeshellarg(dirname(__DIR__)),
            escapeshellarg($phpunitXml)
        );
        exec($command, $output, $returnCode);

        // Then: The test should pass and results should be saved
        $this->assertEquals(0, $returnCode, 'PHPUnit should exit with 0. Output: ' . implode("\n", $output));
        $jsonPath = $this->tempDir . '/.claude/tdd-guard/data/test.json';
        $this->assertFileExists($jsonPath);
        $data = json_decode(file_get_contents($jsonPath), true);
        $this->assertArrayHasKey('testModules', $data);
        $this->assertCount(1, $data['testModules']);

        $module = $data['testModules'][0];
        $this->assertStringContainsString('ExampleTest.php', $module['moduleId']);
        $this->assertCount(1, $module['tests']);

        $test = $module['tests'][0];
        $this->assertEquals('testPassing', $test['name']);
        $this->assertEquals('ExampleTest::testPassing', $test['fullName']);
        $this->assertEquals('passed', $test['state']);
    }
}
