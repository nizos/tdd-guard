# TDD Guard PHPUnit Reporter

PHPUnit reporter that captures test results for TDD Guard validation.

## Requirements

- PHP 8.1+
- PHPUnit 9.0+ or 10.0+ or 11.0+ or 12.0+
- [TDD Guard](https://github.com/nizos/tdd-guard) installed globally

## Installation

```bash
composer require --dev tdd-guard/phpunit
```

## Configuration

### PHPUnit 10+ Configuration

Add the extension to your `phpunit.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<phpunit xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:noNamespaceSchemaLocation="vendor/phpunit/phpunit/phpunit.xsd"
         bootstrap="vendor/autoload.php">
    <testsuites>
        <testsuite name="Application Test Suite">
            <directory>tests</directory>
        </testsuite>
    </testsuites>
    
    <extensions>
        <bootstrap class="TddGuard\PHPUnit\TddGuardExtension">
            <parameter name="projectRoot" value="/absolute/path/to/project/root"/>
        </bootstrap>
    </extensions>
</phpunit>
```

### PHPUnit 9.x Configuration

Add the listener to your `phpunit.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<phpunit bootstrap="vendor/autoload.php">
    <testsuites>
        <testsuite name="Application Test Suite">
            <directory>tests</directory>
        </testsuite>
    </testsuites>
    
    <listeners>
        <listener class="TddGuard\PHPUnit\TddGuardListener">
            <arguments>
                <string>/absolute/path/to/project/root</string>
            </arguments>
        </listener>
    </listeners>
</phpunit>
```

### Project Root Configuration

Set the project root using any ONE of these methods:

**Option 1: PHPUnit Configuration (Recommended)**

Use the `projectRoot` parameter in your `phpunit.xml` (see examples above).

**Option 2: Environment Variable**

```bash
export TDD_GUARD_PROJECT_ROOT=/absolute/path/to/project/root
```

### Configuration Rules

- Project root must be configured via the `projectRoot` parameter or `TDD_GUARD_PROJECT_ROOT` environment variable
- Tests must be run from somewhere within the project root directory
- Relative paths are supported but resolve against the working directory at runtime — if tests may run from different directories, use an absolute path to ensure results are always written to the correct location

## More Information

- Test results are saved to `.claude/tdd-guard/data/test.json`
- See [TDD Guard documentation](https://github.com/nizos/tdd-guard) for complete setup

## License

MIT