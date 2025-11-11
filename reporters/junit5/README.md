# TDD Guard JUnit5 Reporter

JUnit 5 Platform reporter that captures test execution data for TDD Guard enforcement.

## Features

- **Auto-discovery**: Automatically registered via JUnit Platform service provider mechanism
- **Self-detection**: Activates only when TDD Guard is present, zero overhead when disabled
- **Complete test capture**: All JUnit 5 test types (standard, @Nested, @ParameterizedTest, @RepeatedTest, @TestFactory)
- **JSON output**: Writes to `.claude/tdd-guard/data/test.json` in TDD Guard standard format
- **Error resilient**: Never fails tests due to reporter errors

## Installation

### Option 1: Direct JAR Download (Recommended - No Authentication Required)

Download the JAR from the latest release:

```bash
# Download to your project's libs directory
mkdir -p libs
curl -L -o libs/tdd-guard-junit5-0.1.0.jar \
  https://github.com/lightspeed/tdd-guard/releases/download/junit5-reporter-v0.1.0/junit5-0.1.0.jar
```

**Gradle**:

```kotlin
dependencies {
    testImplementation(files("libs/tdd-guard-junit5-0.1.0.jar"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
}

tasks.test {
    systemProperty("tddguard.projectRoot", project.rootDir.absolutePath)
    environment("TDDGUARD_ENABLED", "true")
}
```

**Maven**:

```bash
# Install to local Maven repository
mvn install:install-file \
  -Dfile=libs/tdd-guard-junit5-0.1.0.jar \
  -DgroupId=com.lightspeed.tddguard \
  -DartifactId=junit5 \
  -Dversion=0.1.0 \
  -Dpackaging=jar
```

Then use as normal dependency in pom.xml (see Option 2 for full Maven config).

### Option 2: GitHub Packages (Requires Authentication)

**Note**: GitHub Packages requires authentication even for public packages. Use Option 1 for simpler setup.

#### Gradle

**Add repository** (for GitHub Packages):

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/lightspeed/tdd-guard")
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
            password = project.findProperty("gpr.token") as String? ?: System.getenv("GITHUB_TOKEN")
        }
    }
}
```

**Add dependency**:

```kotlin
dependencies {
    testImplementation("com.lightspeed.tddguard:junit5:0.1.0")
}
```

**Configure test task** (required for project root detection):

```kotlin
tasks.test {
    systemProperty("tddguard.projectRoot", project.rootDir.absolutePath)
    environment("TDDGUARD_ENABLED", "true")

    // Optional: Configure custom source directories
    // systemProperty("tddguard.testSourceDirs", "test,src/test/java,src/test/kotlin")
    // systemProperty("tddguard.mainSourceDirs", "src,src/main/java,src/main/kotlin")
}
```

**Custom Source Directory Configuration** (optional):

By default, the reporter looks for tests in standard Maven/Gradle locations (`src/test/java`, `src/test/kotlin`) and main code in (`src/main/java`, `src/main/kotlin`). For non-standard project layouts, configure custom paths:

```kotlin
tasks.test {
    // Comma-separated list of test source directories
    systemProperty("tddguard.testSourceDirs", "test,integration-test,src/test/java")

    // Comma-separated list of main source directories
    systemProperty("tddguard.mainSourceDirs", "src,main,src/main/java")
}
```

Or use environment variables:

```bash
export TDDGUARD_TEST_SOURCE_DIRS="test,integration-test"
export TDDGUARD_MAIN_SOURCE_DIRS="src,main"
```

**Configuration precedence**: System properties → Environment variables → Defaults

**Authentication**: Set GitHub credentials in `~/.gradle/gradle.properties`:

```properties
gpr.user=YOUR_GITHUB_USERNAME
gpr.token=YOUR_GITHUB_TOKEN
```

Or use environment variables: `GITHUB_ACTOR` and `GITHUB_TOKEN`

### Maven

**Add dependency**:

```xml
<dependency>
    <groupId>com.lightspeed.tddguard</groupId>
    <artifactId>junit5</artifactId>
    <version>0.1.0</version>
    <scope>test</scope>
</dependency>
```

**Configure Surefire plugin** (required for system properties):

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-surefire-plugin</artifactId>
            <version>3.0.0</version>
            <configuration>
                <systemPropertyVariables>
                    <tddguard.projectRoot>${project.basedir}</tddguard.projectRoot>
                    <!-- Optional: Configure custom source directories -->
                    <!-- <tddguard.testSourceDirs>test,src/test/java,src/test/kotlin</tddguard.testSourceDirs> -->
                    <!-- <tddguard.mainSourceDirs>src,src/main/java,src/main/kotlin</tddguard.mainSourceDirs> -->
                </systemPropertyVariables>
                <environmentVariables>
                    <TDDGUARD_ENABLED>true</TDDGUARD_ENABLED>
                </environmentVariables>
            </configuration>
        </plugin>
    </plugins>
</build>
```

**Add repository** (for GitHub Packages):

```xml
<repositories>
    <repository>
        <id>github-tdd-guard</id>
        <url>https://maven.pkg.github.com/lightspeed/tdd-guard</url>
    </repository>
</repositories>
```

And configure authentication in `~/.m2/settings.xml`:

```xml
<servers>
    <server>
        <id>github-tdd-guard</id>
        <username>YOUR_GITHUB_USERNAME</username>
        <password>YOUR_GITHUB_TOKEN</password>
    </server>
</servers>
```

## Activation

The reporter auto-enables when either condition is met:

1. **Environment variable**: `TDDGUARD_ENABLED=true`
2. **Directory exists**: `.claude/tdd-guard/` in project root

When disabled, all methods return immediately with <100µs overhead per test.

## Configuration

### Project Root Resolution

The reporter uses this fallback hierarchy to find the project root:

1. System property: `tddguard.projectRoot`
2. Environment variable: `TDDGUARD_PROJECT_ROOT`
3. Traverse up from working directory until finding `build.gradle`, `build.gradle.kts`, or `pom.xml`
4. Fallback to current working directory

### Source Directory Resolution

The reporter automatically detects test and main source directories for accurate file path resolution. This is particularly useful for projects with non-standard layouts or multi-module structures.

**Default Behavior** (no configuration needed):

- Test sources: `src/test/java`, `src/test/kotlin`, `src/test`
- Main sources: `src/main/java`, `src/main/kotlin`, `src/main`

**Custom Configuration**:

For non-standard project layouts, configure custom source directories using system properties or environment variables:

**System Properties** (Gradle example):

```kotlin
tasks.test {
    systemProperty("tddguard.testSourceDirs", "test,integration-test,src/test/java")
    systemProperty("tddguard.mainSourceDirs", "src,main,src/main/java")
}
```

**System Properties** (Maven example):

```xml
<systemPropertyVariables>
    <tddguard.testSourceDirs>test,src/test/java,src/test/kotlin</tddguard.testSourceDirs>
    <tddguard.mainSourceDirs>src,src/main/java,src/main/kotlin</tddguard.mainSourceDirs>
</systemPropertyVariables>
```

**Environment Variables**:

```bash
export TDDGUARD_TEST_SOURCE_DIRS="test,integration-test"
export TDDGUARD_MAIN_SOURCE_DIRS="src,main"
```

**Resolution Hierarchy**:

1. System properties (`tddguard.testSourceDirs`, `tddguard.mainSourceDirs`)
2. Environment variables (`TDDGUARD_TEST_SOURCE_DIRS`, `TDDGUARD_MAIN_SOURCE_DIRS`)
3. Default conventions (standard Maven/Gradle layout)

**Format**: Comma-separated list of relative paths from project root. Paths can be:

- Simple directory names: `test,src`
- Full paths: `src/test/java,src/test/kotlin`
- Mixed: `test,integration-test,src/test/java`

The reporter will check files against all configured directories to determine if they are test or main source files.

## JSON Output Format

```json
{
  "framework": "junit5",
  "timestamp": "2025-11-06T12:00:00Z",
  "duration": 1500,
  "summary": {
    "total": 10,
    "passed": 8,
    "failed": 1,
    "skipped": 1
  },
  "tests": [
    {
      "name": "testMethod",
      "file": "src/test/java/com/example/MyTest.java",
      "status": "passed",
      "duration": 250,
      "displayName": "testMethod()"
    }
  ],
  "failures": [
    {
      "name": "failedTest",
      "file": "src/test/java/com/example/MyTest.java",
      "message": "Expected 5 but was 3",
      "stack": "java.lang.AssertionError: ..."
    }
  ],
  "educational": []
}
```

## Supported Test Types

- **Standard tests**: `@Test`
- **Nested tests**: `@Nested`
- **Parameterized tests**: `@ParameterizedTest`
- **Repeated tests**: `@RepeatedTest`
- **Dynamic tests**: `@TestFactory`
- **JUnit 4 Vintage**: Tests running through JUnit Vintage engine

## Implementation Details

### Architecture

- `TddGuardListener`: Main TestExecutionListener implementation
- `ProjectRootResolver`: Resolves project root using fallback hierarchy
- `TestResultCollector`: Captures test lifecycle events
- `TestJsonWriter`: Writes results to JSON with atomic file operations
- `model/`: POJO classes matching TDD Guard JSON schema

### Performance

- **Disabled**: <100µs overhead per test (single boolean check)
- **Enabled**: <1ms per test (event capture and collection)

### Error Handling

All exceptions are caught and logged to stderr without failing tests:

```
TDD Guard: Error writing test results: <message>
```

## Development

### Build

```bash
gradle build
```

### Test

```bash
gradle test
```

### Test Structure

- `src/test/java/.../`: Unit tests for individual components
- `src/test/java/.../integration/`: Integration tests with real JUnit Platform execution
- `src/test/java/.../integration/SampleTests.java`: Sample tests (passed, failed, skipped)
- `src/test/java/.../integration/AdvancedTestTypes.java`: Tests for @Parameterized, @Repeated, @TestFactory

## Requirements

- Java 11+ (target)
- Java 21 (build)
- JUnit Platform 1.10.1
- Gson 2.10.1

## License

MIT
