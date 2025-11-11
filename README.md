# TDD Guard

[![npm version](https://badge.fury.io/js/tdd-guard.svg)](https://www.npmjs.com/package/tdd-guard)
[![CI](https://github.com/nizos/tdd-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/nizos/tdd-guard/actions/workflows/ci.yml)
[![Security](https://github.com/nizos/tdd-guard/actions/workflows/security.yml/badge.svg)](https://github.com/nizos/tdd-guard/actions/workflows/security.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

Automated Test-Driven Development enforcement for Claude Code.

## Overview

TDD Guard ensures Claude Code follows Test-Driven Development principles. When your agent tries to skip tests or over-implement, TDD Guard blocks the action and explains what needs to happen instead.

<p align="center">
  <a href="https://nizar.se/uploads/videos/tdd-guard-demo.mp4">
    <img src="docs/assets/tdd-guard-demo-screenshot.gif" alt="TDD Guard Demo" width="600">
  </a>
  <br>
  <em>Click to watch TDD Guard in action</em>
</p>

## Features

- **Test-First Enforcement** - Blocks implementation without failing tests
- **Minimal Implementation** - Prevents code beyond current test requirements
- **Lint Integration** - Enforces refactoring using your linting rules
- **Multi-Language Support** - TypeScript, JavaScript, Python, PHP, Go, Rust, Java, and Kotlin
- **Customizable Rules** - Adjust validation rules to match your TDD style
- **Flexible Validation** - Choose faster or more capable models for your needs
- **Session Control** - Toggle on and off mid-session

## Requirements

- Node.js 22+
- Claude Code or Anthropic API key
- Test framework (Jest, Vitest, pytest, PHPUnit, Go 1.24+, Rust with cargo/cargo-nextest, or JUnit 5)

## Quick Start

### 1. Install TDD Guard

Using npm:

```bash
npm install -g tdd-guard
```

Or using Homebrew:

```bash
brew install tdd-guard
```

### 2. Add Test Reporter

TDD Guard needs to capture test results from your test runner. Choose your language below:

<details>
<summary><b>JavaScript/TypeScript</b></summary>

Choose your test runner:

#### Vitest

Install the [tdd-guard-vitest](https://www.npmjs.com/package/tdd-guard-vitest) reporter in your project:

```bash
npm install --save-dev tdd-guard-vitest
```

Add to your `vitest.config.ts`:

```typescript
import { defineConfig } from 'vitest/config'
import { VitestReporter } from 'tdd-guard-vitest'

export default defineConfig({
  test: {
    reporters: [
      'default',
      new VitestReporter('/Users/username/projects/my-app'),
    ],
  },
})
```

#### Jest

Install the [tdd-guard-jest](https://www.npmjs.com/package/tdd-guard-jest) reporter in your project:

```bash
npm install --save-dev tdd-guard-jest
```

Add to your `jest.config.ts`:

```typescript
import type { Config } from 'jest'

const config: Config = {
  reporters: [
    'default',
    [
      'tdd-guard-jest',
      {
        projectRoot: '/Users/username/projects/my-app',
      },
    ],
  ],
}

export default config
```

**Note:** For both Vitest and Jest, specify the project root path when your test config is not at the project root (e.g., in workspaces or monorepos). This ensures TDD Guard can find the test results. See the reporter configuration docs for more details:

- [Vitest configuration](reporters/vitest/README.md#configuration)
- [Jest configuration](reporters/jest/README.md#configuration)

</details>

<details>
<summary><b>Python (pytest)</b></summary>

Install the [tdd-guard-pytest](https://pypi.org/project/tdd-guard-pytest) reporter:

```bash
pip install tdd-guard-pytest
```

Configure the project root in your `pyproject.toml`:

```toml
[tool.pytest.ini_options]
tdd_guard_project_root = "/Users/username/projects/my-app"
```

**Note:** Specify the project root path when your tests run from a subdirectory or in a monorepo setup. This ensures TDD Guard can find the test results. See the [pytest reporter configuration](reporters/pytest/README.md#configuration) for alternative configuration methods (pytest.ini, setup.cfg).

</details>

<details>
<summary><b>PHP (PHPUnit)</b></summary>

Install the [tdd-guard/phpunit](https://packagist.org/packages/tdd-guard/phpunit) reporter in your project:

```bash
composer require --dev tdd-guard/phpunit
```

For PHPUnit 9.x, add to your `phpunit.xml`:

```xml
<listeners>
    <listener class="TddGuard\PHPUnit\TddGuardListener">
        <arguments>
            <string>/Users/username/projects/my-app</string>
        </arguments>
    </listener>
</listeners>
```

For PHPUnit 10.x/11.x/12.x, add to your `phpunit.xml`:

```xml
<extensions>
    <bootstrap class="TddGuard\PHPUnit\TddGuardExtension">
        <parameter name="projectRoot" value="/Users/username/projects/my-app"/>
    </bootstrap>
</extensions>
```

**Note:** Specify the project root path when your phpunit.xml is not at the project root (e.g., in subdirectories or monorepos). This ensures TDD Guard can find the test results. The reporter saves results to `.claude/tdd-guard/data/test.json`.

</details>

<details>
<summary><b>Go</b></summary>

Install the tdd-guard-go reporter:

```bash
go install github.com/nizos/tdd-guard/reporters/go/cmd/tdd-guard-go@latest
```

Pipe `go test -json` output to the reporter:

```bash
go test -json ./... 2>&1 | tdd-guard-go -project-root /Users/username/projects/my-app
```

For Makefile integration:

```makefile
test:
	go test -json ./... 2>&1 | tdd-guard-go -project-root /Users/username/projects/my-app
```

**Note:** The reporter acts as a filter that passes test output through unchanged while capturing results for TDD Guard. See the [Go reporter configuration](reporters/go/README.md#configuration) for more details.

</details>

<details>
<summary><b>Rust</b></summary>

Install the [tdd-guard-rust](https://crates.io/crates/tdd-guard-rust) reporter:

```bash
cargo install tdd-guard-rust
```

Use it to capture test results from `cargo test` or `cargo nextest`:

```bash
# With nextest (recommended)
cargo nextest run 2>&1 | tdd-guard-rust --project-root /Users/username/projects/my-app --passthrough

# With cargo test
cargo test -- -Z unstable-options --format json 2>&1 | tdd-guard-rust --project-root /Users/username/projects/my-app --passthrough
```

For Makefile integration:

```makefile
test:
	cargo nextest run 2>&1 | tdd-guard-rust --project-root $(PWD) --passthrough
```

**Note:** The reporter acts as a filter that passes test output through unchanged while capturing results for TDD Guard. See the [Rust reporter configuration](reporters/rust/README.md#configuration) for more details.

</details>

<details>
<summary><b>Java/Kotlin (JUnit 5)</b></summary>

Download the reporter JAR from releases:

```bash
curl -L -o libs/tdd-guard-junit5.jar \
  https://github.com/lightspeed/tdd-guard/releases/latest/download/junit5-0.1.0.jar
```

**Gradle**:

```kotlin
dependencies {
    testImplementation(files("libs/tdd-guard-junit5.jar"))
}

tasks.test {
    systemProperty("tddguard.projectRoot", project.rootDir.absolutePath)
    environment("TDDGUARD_ENABLED", "true")
}
```

**Maven**:

```bash
mvn install:install-file \
  -Dfile=libs/tdd-guard-junit5.jar \
  -DgroupId=com.lightspeed.tddguard \
  -DartifactId=junit5 \
  -Dversion=0.1.0 \
  -Dpackaging=jar
```

Then add dependency in pom.xml and configure Surefire plugin.

**Note:** For GitHub Packages installation with automatic dependency resolution, see [reporters/junit5/README.md](reporters/junit5/README.md). The direct JAR download method requires no authentication.

</details>

### 3. Configure Claude Code Hooks

TDD Guard uses hooks to validate operations and provide convenience features like quick toggle commands and automatic session management.

Choose either interactive or manual setup below:

<details>
<summary><b>Interactive Setup</b></summary>

Type `/hooks` in Claude Code to open the hooks menu, then configure each hook. Use the same location for all hooks. See [Settings File Locations](docs/configuration.md#settings-file-locations) for guidance.

**PreToolUse Hook**

1. Select `PreToolUse - Before tool execution`
2. Choose `+ Add new matcher...` and enter: `Write|Edit|MultiEdit|TodoWrite`
3. Select `+ Add new hook...` and enter: `tdd-guard`
4. Choose where to save

**UserPromptSubmit Hook**

1. Select `UserPromptSubmit - When the user submits a prompt`
2. Select `+ Add new hook...` and enter: `tdd-guard`
3. Choose same location as PreToolUse

**SessionStart Hook**

1. Select `SessionStart - When a new session is started`
2. Select `+ Add new matcher...` and enter: `startup|resume|clear`
3. Select `+ Add new hook...` and enter: `tdd-guard`
4. Choose same location as previous hooks

</details>

<details>
<summary><b>Manual Configuration</b></summary>

If you prefer to edit settings files directly, add all three hooks to your chosen settings file. See [Settings File Locations](docs/configuration.md#settings-file-locations) to choose the appropriate file:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Write|Edit|MultiEdit|TodoWrite",
        "hooks": [
          {
            "type": "command",
            "command": "tdd-guard"
          }
        ]
      }
    ],
    "userpromptsubmit": [
      {
        "hooks": [
          {
            "type": "command",
            "command": "tdd-guard"
          }
        ]
      }
    ],
    "SessionStart": [
      {
        "matcher": "startup|resume|clear",
        "hooks": [
          {
            "type": "command",
            "command": "tdd-guard"
          }
        ]
      }
    ]
  }
}
```

</details>

## Additional Configuration

- [Custom instructions](docs/custom-instructions.md) - Customize TDD validation rules
- [Lint integration](docs/linting.md) - Automated refactoring support
- [Strengthening enforcement](docs/enforcement.md) - Prevent agents from bypassing validation
- [Ignore patterns](docs/ignore-patterns.md) - Control which files are validated
- [Validation Model](docs/validation-model.md) - Choose faster or more capable model

## Security Notice

As stated in the [Claude Code Hooks documentation](https://docs.anthropic.com/en/docs/claude-code/hooks#security-considerations):

> Hooks execute shell commands with your full user permissions without confirmation. You are responsible for ensuring your hooks are safe and secure. Anthropic is not liable for any data loss or system damage resulting from hook usage.

We share this information for transparency. Please read the full [security considerations](https://docs.anthropic.com/en/docs/claude-code/hooks#security-considerations) before using hooks.

TDD Guard runs with your user permissions and has access to your file system. We follow security best practices including automated security scanning, dependency audits, and test-driven development. Review the source code if you have security concerns.

## Roadmap

- Add support for more testing frameworks (Mocha, unittest, etc.)
- Add support for additional programming languages (Ruby, Java, C#, etc.)
- Validate file modifications made through MCPs and shell commands
- Add integration for OpenCode and other vendor-agnostic AI coding tools
- Encourage meaningful refactoring opportunities when tests are green
- Add support for multiple concurrent sessions per project

## Development

- [Development Guide](DEVELOPMENT.md) - Setup instructions and development guidelines
- [Architecture Decision Records](docs/adr/) - Technical design decisions and rationale

## Contributing

Contributions are welcome! Feel free to submit issues and pull requests.

**Contributors:**

- Python/pytest support: [@Durafen](https://github.com/Durafen)
- PHP/PHPUnit support: [@wazum](https://github.com/wazum)
- Rust/cargo support: [@104hp6u](https://github.com/104hp6u)
- Go support: [@sQVe](https://github.com/sQVe), [@wizzomafizzo](https://github.com/wizzomafizzo)

## Support

- [Configuration](docs/configuration.md) - Complete settings documentation
- [Discussions](https://github.com/nizos/tdd-guard/discussions) - Ask questions and share ideas
- [Issues](https://github.com/nizos/tdd-guard/issues) - Report bugs and request features

## License

[MIT](LICENSE)
