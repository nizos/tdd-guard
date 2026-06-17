# Configuration Guide

This guide covers the configuration options for TDD Guard.

## Environment Variables

TDD Guard uses environment variables for configuration.
Create a `.env` file in your project root:

**Note:** If you're migrating from an older version using `MODEL_TYPE`, see the [Configuration Migration Guide](config-migration.md).

```bash
# Validation client for TDD enforcement (optional)
# Options: 'sdk' (default) or 'api'
VALIDATION_CLIENT=sdk

# Model version for validation (optional)
# Default: claude-sonnet-4-6
# See https://docs.anthropic.com/en/docs/about-claude/models/overview
TDD_GUARD_MODEL_VERSION=claude-sonnet-4-6

# Anthropic API Key
# Required when VALIDATION_CLIENT is set to 'api'
# Get your API key from https://console.anthropic.com/
TDD_GUARD_ANTHROPIC_API_KEY=your-api-key-here

# Anthropic API Base URL (optional)
# Use for alternative API providers like Z.AI (GLM models)
# See docs/validation-model.md for Z.AI configuration
TDD_GUARD_ANTHROPIC_BASE_URL=https://api.z.ai/api/anthropic

# Linter type for refactoring phase support (optional)
# Options: 'eslint', 'golangci-lint' or unset (no linting)
# See docs/linting.md for detailed setup and configuration
LINTER_TYPE=eslint
```

## Model Configuration

TDD Guard supports multiple validation clients:

- **SDK** (default) - Uses your Claude Code subscription
- **API** - Separate billing for CI/CD or faster validation
- **CLI** (deprecated) - Legacy option, not recommended

For detailed configuration, billing information, and troubleshooting, see the [Validation Model Configuration](validation-model.md) guide.

If you're using the deprecated CLI client, see the [Configuration Migration Guide](config-migration.md#cli-binary-configuration).

## Settings File Locations

Choose where to save your settings based on your needs:

- **Project settings** (`.claude/settings.json`) - Recommended for team consistency
- **Local settings** (`.claude/settings.local.json`) - For personal preferences
- **User settings** (`~/.claude/settings.json`) - For global configuration

## Hook Configuration

If you installed TDD Guard as a [plugin](../plugin/README.md), hooks are configured automatically. For manual hook setup, see the [installation guide](installation.md#2-configure-claude-code-hooks).

**Related:**

- [Quick commands](quick-commands.md) - Enable `tdd-guard on/off` commands
- [Session management](session-management.md) - Automatic data clearing and rule setup
- [Lint integration](linting.md) - Automated refactoring support
- [Strengthening enforcement](enforcement.md) - Prevent agents from bypassing validation

## Test Reporter Configuration

- [Manual Installation](installation.md#3-add-test-reporter) - Setup guide for all supported reporters

- **JavaScript/TypeScript**:
  - [Vitest reporter configuration](../reporters/vitest/README.md#configuration)
  - [Jest reporter configuration](../reporters/jest/README.md#configuration)
- **Python**: See [Pytest reporter configuration](../reporters/pytest/README.md#configuration)
- **PHP**: See [PHPUnit reporter configuration](../reporters/phpunit/README.md#configuration)
- **Go**: See [Go reporter configuration](../reporters/go/README.md#configuration)
- **Rust**: See [Rust reporter configuration](../reporters/rust/README.md#configuration)

## Custom Validation Rules

See [Custom Instructions](custom-instructions.md) to customize TDD validation rules to match your practices.

## Data Storage

TDD Guard stores context data in `.claude/tdd-guard/data/`:

- `instructions.md` - Your custom TDD validation rules (created automatically, never overwritten)
- `test.json` - Latest test results from your test runner (Vitest or pytest)
- `todos.json` - Current todo state
- `modifications.json` - File modification history
- `lint.json` - ESLint results (only created when LINTER_TYPE=eslint)

This directory is created automatically and should be added to `.gitignore`.

## Troubleshooting

### Dependency Versions

#### Vitest

Use the latest Vitest version to ensure correct test output format for TDD Guard:

```bash
npm install --save-dev vitest@latest
```

#### pytest

For Python projects, ensure you have a recent version of pytest:

```bash
pip install pytest>=7.0.0
```

### Common Issues

1. **TDD Guard not triggering**: Check that hooks are properly configured in `.claude/settings.json`
2. **Test results not captured**: Ensure `VitestReporter` is added to your Vitest config
3. **"Command not found" errors**: Make sure `tdd-guard` is installed globally with `npm install -g tdd-guard`
4. **Changes not taking effect**: Restart your Claude session after modifying hooks or environment variables

### Updating TDD Guard

To update to the latest version:

```bash
# Update CLI tool
npm update -g tdd-guard

# For JavaScript/TypeScript projects, update the Vitest reporter in your project
npm update tdd-guard-vitest

# For Python projects, update the pytest reporter
pip install --upgrade tdd-guard-pytest
```

Check your current version:

```bash
npm list -g tdd-guard
pip show tdd-guard-pytest
```
