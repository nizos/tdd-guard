# TDD Guard Rust Reporter

Rust test reporter that captures test results for TDD Guard validation.

**Note:** This reporter is part of the [TDD Guard](https://github.com/nizos/tdd-guard) project, which ensures Claude Code follows Test-Driven Development principles.

## Requirements

- Rust 1.70+
- TDD Guard installed globally
- `cargo-nextest` (recommended) or `cargo test` with JSON output support

## Installation

### Step 1: Install TDD Guard

```bash
npm install -g tdd-guard
```

### Step 2: Install the Rust reporter

```bash
cargo install tdd-guard-rust
```

## Usage

The reporter works as a filter that processes test output while passing it through unchanged.

### With cargo-nextest (Recommended)

```bash
cargo nextest run 2>&1 | tdd-guard-rust --project-root path/to/project
```

### With cargo test

```bash
cargo test -- -Z unstable-options --format json 2>&1 | tdd-guard-rust --project-root path/to/project
```

### Direct Execution

The reporter can also execute tests directly:

```bash
# Auto-detect runner (prefers nextest)
tdd-guard-rust --project-root path/to/project

# Force specific runner
tdd-guard-rust --project-root path/to/project --runner nextest
tdd-guard-rust --project-root path/to/project --runner cargo
```

## Makefile Integration

Add to your `Makefile`:

```makefile
.PHONY: test
test:
	cargo nextest run 2>&1 | tdd-guard-rust --project-root $(PWD) --passthrough

.PHONY: test-tdd
test-tdd:
	tdd-guard on && $(MAKE) test
```

## Configuration

### Project Root

The `--project-root` flag sets the project root directory. Test results are written to `.claude/tdd-guard/data/test.json` relative to this path. If omitted, defaults to the current working directory.

### Flags

- `--passthrough`: Force passthrough mode even if stdin is a terminal
- `--runner [auto|nextest|cargo]`: Choose test runner for direct execution (default: auto)
- `--project-root`: Path to project root directory (defaults to current working directory)

## How It Works

The reporter captures JSON-formatted test output, passes it through unchanged to stdout, and saves TDD Guard-formatted results to `.claude/tdd-guard/data/test.json`.

## License

MIT - See LICENSE file in the repository root.
