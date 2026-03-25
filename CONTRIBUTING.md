# Contributing

## Core Requirements

Implementation must be test driven with all relevant and affected tests passing. Run linting and formatting (`npm run checks`) and ensure the build succeeds (`npm run build`).

## Pull Requests

Create focused PRs with meaningful titles that describe what the change accomplishes. The description must explain what the PR introduces and why it's needed. Document any important design decisions or architectural choices. Keep PRs small and focused for easier review and incremental feedback.

## Commit Messages

Use conventional commits and communicate the why, not just what. Focus on the reasoning behind changes rather than describing what was changed.

## Reporter Contributions

Project root path can be specified so that tests can be run from any directory in the project. For security, validate that the project root path is absolute and that it is the current working directory or an ancestor of it. Relevant cases must be added to reporter integration tests.

#### Build Error Handling for Compiled and Typed Languages

Reporters for compiled languages must produce synthetic test failures for compilation errors. When a build fails before tests can run, the reporter should emit a failed test entry with the compiler diagnostics as error messages. Without this, compilation failures produce empty output and the validation agent has no signal that something is broken. The Go and Rust reporters serve as reference implementations (search for `CompilationError` in `reporters/go/internal/parser/parser.go` and `compilation::build` in `reporters/rust/src/transformer.rs`).

If your reporter introduces a new language, update the pre-filter's file type detection so that single test additions can be allowed through without full validation. See `src/hooks/fileTypeDetection.ts` for language and test file pattern detection.

## Style Guidelines

No emojis in code or documentation. Avoid generic or boilerplate content. Be deliberate and intentional. Keep it clean and concise.

## Development

- [Development Guide](DEVELOPMENT.md) - Setup instructions and testing
- [Dev Container setup](.devcontainer/README.md) - Consistent development environment
