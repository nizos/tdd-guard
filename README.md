# TDD Guard

[![npm version](https://badge.fury.io/js/tdd-guard.svg)](https://www.npmjs.com/package/tdd-guard)
[![npm downloads](https://img.shields.io/npm/dt/tdd-guard)](https://www.npmjs.com/package/tdd-guard)
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
- **Customizable Rules** - Adjust validation rules to match your TDD style
- **Flexible Validation** - Choose faster or more capable models for your needs
- **Session Control** - Toggle on and off mid-session

## Getting Started

### Requirements

- Node.js 22+
- A supported test framework (Vitest, Jest, Storybook, pytest, PHPUnit, Go, Rust)

### Installation

Open Claude Code in your project and run:

1. `/plugin marketplace add nizos/tdd-guard`
2. `/plugin install tdd-guard@tdd-guard`
3. `/tdd-guard:setup`

This adds the marketplace, installs the plugin, and configures the test reporter for your project. You may need to restart your terminal session or IDE extension for the setup skill to appear. For manual installation and configuration, see the [installation guide](docs/installation.md).

> Multiple installation methods (Homebrew, npm, npx) can conflict and cause unexpected behaviour. The recommended installation is via the Claude Code plugin. Follow the steps above.

## Configuration

- [Custom instructions](docs/custom-instructions.md) - Customize TDD validation rules
- [Lint integration](docs/linting.md) - Automated refactoring support
- [Strengthening enforcement](docs/enforcement.md) - Prevent agents from bypassing validation
- [Ignore patterns](docs/ignore-patterns.md) - Control which files are validated
- [Validation Model](docs/validation-model.md) - Choose faster or more capable model
- [All settings](docs/configuration.md) - Complete settings documentation

## Security

TDD Guard hooks run with your user permissions. We maintain automated security scanning, dependency audits, and welcome source code review. See [Claude Code's security considerations](https://docs.anthropic.com/en/docs/claude-code/hooks#security-considerations) for more on hook safety.

## Development

### Contributing

Contributions are welcome! See the [contributing guidelines](CONTRIBUTING.md) to get started.

**Contributors:**

- Python/pytest support: [@Durafen](https://github.com/Durafen)
- PHP/PHPUnit support: [@wazum](https://github.com/wazum)
- Rust/cargo support: [@104hp6u](https://github.com/104hp6u)
- Go support: [@sQVe](https://github.com/sQVe), [@wizzomafizzo](https://github.com/wizzomafizzo)
- Storybook support: [@akornmeier](https://github.com/akornmeier)

### Roadmap

- Expand language and test framework support
- Validate file modifications made through MCPs and shell commands
- Encourage meaningful refactoring opportunities when tests are green
- Add support for multiple concurrent sessions per project

## Support

- [Discussions](https://github.com/nizos/tdd-guard/discussions) - Ask questions and share ideas
- [Issues](https://github.com/nizos/tdd-guard/issues) - Report bugs and request features

## License

[MIT](LICENSE)
