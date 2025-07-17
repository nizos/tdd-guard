# TDD Guard

[![npm version](https://badge.fury.io/js/tdd-guard.svg)](https://www.npmjs.com/package/tdd-guard)
[![CI](https://github.com/nizos/tdd-guard/actions/workflows/ci.yml/badge.svg)](https://github.com/nizos/tdd-guard/actions/workflows/ci.yml)
[![Security](https://github.com/nizos/tdd-guard/actions/workflows/security.yml/badge.svg)](https://github.com/nizos/tdd-guard/actions/workflows/security.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

TDD enforcement for Claude Code.

## Overview

TDD Guard monitors file operations in real-time and blocks changes that violate TDD principles. It provides immediate feedback when violations occur, helping maintain disciplined development practices without manual oversight.

The tool intercepts Write, Edit, and MultiEdit operations before execution, examining them against the current development state. Each validation considers the file path, intended modifications, current todo list, and latest test results to determine whether changes follow proper TDD practices.

## Core Benefits

- **Eliminates manual TDD enforcement**: No need to remind agents about writing tests first or following TDD cycles
- **Preserves context window**: Removes lengthy TDD instructions from `CLAUDE.md`, freeing space for domain-specific guidance
- **Maintains development flow**: Works silently until a violation occurs, then provides clear corrective feedback

## Technical Features

- **Multi-model Support**: Choose between Claude Code CLI or Anthropic API
- **Context Aggregation**: Combines file modifications, todo states, and test results for accurate validation
- **Dynamic Instructions**: Tailored prompts for Write, Edit, and MultiEdit operations

## Requirements

- **Node.js**: Version 18 or higher
- **Test Runner**: Vitest (JavaScript/TypeScript) or pytest (Python)
- **ESLint**: Latest version recommended for refactoring phase support

## Installation

### For JavaScript/TypeScript Projects

```bash
npm install --save-dev tdd-guard
```

### For Python Projects

```bash
git clone https://github.com/nizos/tdd-guard
cd tdd-guard
pip install -e .
```

## Quick Start

### 1. Environment

If you do not have Claude installed locally, create a `.env` file:

```bash
USE_SYSTEM_CLAUDE=true
```

This tells TDD Guard to use System Claude from PATH instead of `~/.claude/local/claude`.

### 2. Hook Setup

Configure TDD Guard using the `/hooks` command:

1. Type `/hooks` in Claude Code
2. Select `PreToolUse - Before tool execution`
3. Choose `+ Add new matcher...`
4. Enter: `Write|Edit|MultiEdit|TodoWrite`
5. Select `+ Add new hook...`
6. Enter command: `tdd-guard`
7. Choose where to save (Project settings recommended)

### 3. Test Reporter

#### For JavaScript/TypeScript (Vitest)

Add the TDD Guard reporter to your `vitest.config.ts`:

```typescript
import { VitestReporter } from 'tdd-guard'

export default defineConfig({
  test: {
    reporters: ['default', new VitestReporter()],
  },
})
```

#### For Python (pytest)

The TDD Guard plugin is automatically discovered when the package is installed. Simply run:

```bash
pytest
```

The plugin will automatically capture test results for TDD validation.

Make sure your test scripts are configured to use your chosen test runner.

For troubleshooting and configuration options, see the [Configuration Guide](docs/CONFIGURATION.md).

## Security Notice

As stated in the [Claude Code Hooks documentation](https://docs.anthropic.com/en/docs/claude-code/hooks#security-considerations):

> Hooks execute shell commands with your full user permissions without confirmation. You are responsible for ensuring your hooks are safe and secure. Anthropic is not liable for any data loss or system damage resulting from hook usage.

We share this information for transparency. Please read the full [security considerations](https://docs.anthropic.com/en/docs/claude-code/hooks#security-considerations) before using hooks.

TDD Guard runs with your user permissions and has access to your file system. We follow security best practices including automated security scanning, dependency audits, and test-driven development. Review the source code if you have security concerns.

## Known Limitations

- Not tested with multiple subagents working simultaneously

## Roadmap

- Add support for other testing frameworks (Jest, Mocha, etc.)
- Add support for other programming languages such as Python
- Encourage meaningful refactoring opportunities when tests are green
- Improve handling of concurrent subagents

## Contributing

Contributions are welcome. Feel free to submit issues and pull requests.

## Documentation

- [Configuration Guide](docs/CONFIGURATION.md)
- [Architecture Decision Records](docs/adr/)

## License

[MIT](LICENSE)
