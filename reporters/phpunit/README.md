# TDD Guard PHPUnit Reporter

PHPUnit reporter that captures test results for TDD Guard validation.

## Requirements

- PHP 8.1+
- PHPUnit 9.0+ or 10.0+ or 11.0+
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

**Option 3: Automatic Detection**

If not configured, the reporter will:
- Use the directory containing `phpunit.xml`
- Fall back to current working directory

### Configuration Rules

- Path must be absolute
- Falls back to current directory if configuration is invalid

## More Information

- Test results are saved to `.claude/tdd-guard/data/test.json`
- See [TDD Guard documentation](https://github.com/nizos/tdd-guard) for complete setup

## License

MIT