<?php

declare(strict_types=1);

namespace TddGuard\PHPUnit\Tests;

use PHPUnit\Framework\TestCase;
use Symfony\Component\Filesystem\Filesystem;

final class TddGuardExtensionFailedTest extends TestCase
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

    public function testExtensionCapturesFailedTest(): void
    {
        // Given: A test file with a failing test
        $testFile = $this->tempDir . '/FailingTest.php';
        file_put_contents($testFile, '<?php
use PHPUnit\Framework\TestCase;
class FailingTest extends TestCase {
    public function testFailing(): void {
        $this->assertEquals(5, 2 + 2, "Math is broken!");
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

        // Then: The test should fail and contain error details
        $this->assertNotEquals(0, $returnCode, 'PHPUnit should exit with non-zero');
        $jsonPath = $this->tempDir . '/.claude/tdd-guard/data/test.json';
        $this->assertFileExists($jsonPath);
        $data = json_decode(file_get_contents($jsonPath), true);
        $this->assertArrayHasKey('testModules', $data);

        $module = $data['testModules'][0];
        $test = $module['tests'][0];
        $this->assertEquals('testFailing', $test['name']);
        $this->assertEquals('FailingTest::testFailing', $test['fullName']);
        $this->assertEquals('failed', $test['state']);
        $this->assertArrayHasKey('errors', $test);
        $this->assertStringContainsString('Math is broken!', $test['errors'][0]['message']);
    }

    public function testExtensionCapturesSkippedTest(): void
    {
        // Given: A test file with a skipped test
        $testFile = $this->tempDir . '/SkippedTest.php';
        file_put_contents($testFile, '<?php
use PHPUnit\Framework\TestCase;
class SkippedTest extends TestCase {
    public function testSkipped(): void {
        $this->markTestSkipped("Not implemented yet");
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

        // Then: The skipped test should be recorded
        $jsonPath = $this->tempDir . '/.claude/tdd-guard/data/test.json';
        $this->assertFileExists($jsonPath);
        $data = json_decode(file_get_contents($jsonPath), true);
        $module = $data['testModules'][0];
        $test = $module['tests'][0];
        $this->assertEquals('testSkipped', $test['name']);
        $this->assertEquals('skipped', $test['state']);
    }
}
