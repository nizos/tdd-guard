# TDD Guard Go Reporter

Go test reporter that captures test results for TDD Guard validation.

## Requirements

- Go 1.24+
- [TDD Guard](https://github.com/nizos/tdd-guard) installed globally

## Installation

```bash
go install github.com/nizos/tdd-guard/reporters/go/cmd/tdd-guard-go@latest
```

## Configuration

### Basic Usage

Pipe `go test -json` output to the reporter:

```bash
go test -json ./... 2>&1 | tdd-guard-go
```

### Project Root Configuration

For projects where tests run in subdirectories, specify the project root:

```bash
go test -json ./... 2>&1 | tdd-guard-go -project-root path/to/project/root
```

### Configuration Rules

- Current directory must be within the configured project root
- Falls back to current directory if not specified

### Makefile Integration

Add to your `Makefile`:

```makefile
test:
	go test -json ./... 2>&1 | tdd-guard-go -project-root path/to/project/root
```

## How It Works

The reporter acts as a filter that:

1. Reads `go test -json` output from stdin
2. Passes the output through to stdout unchanged
3. Parses test results and transforms them to TDD Guard format
4. Saves results to `.claude/tdd-guard/data/test.json`

This design allows it to be inserted into existing test pipelines without disrupting output.

## More Information

- Test results are saved to `.claude/tdd-guard/data/test.json`
- See [TDD Guard documentation](https://github.com/nizos/tdd-guard) for complete setup

## License

MIT
