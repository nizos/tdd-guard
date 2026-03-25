# Contributing

## Core Requirements

Implementation must be test driven with all relevant and affected tests passing. Run linting and formatting (`npm run checks`) and ensure the build succeeds (`npm run build`).

## Pull Requests

Create focused PRs with meaningful titles that describe what the change accomplishes. The description must explain what the PR introduces and why it's needed. Document any important design decisions or architectural choices. Keep PRs small and focused for easier review and incremental feedback.

## Commit Messages

Use conventional commits and communicate the why, not just what. Focus on the reasoning behind changes rather than describing what was changed.

## Reporter Contributions

Project root path can be specified so that tests can be run from any directory in the project. For security, validate that the project root path is absolute and that it is the current working directory or an ancestor of it. Relevant cases must be added to reporter integration tests.

#### Build Error handling for Compiled and Typed Languages

For compiled languages, the reporter must handle build failures explicitly. When compilation fails, no tests run and no test results are produced. To keep the core validation layer language-agnostic, reporters for compiled languages should detect build failures and write a synthetic test entry with a failed state to `test.json`. This allows the validator to reason about the failure without requiring special-casing in the core.

See the Go reporter (`reporters/go/internal/parser/parser.go`, search for `CompilationError`) and Rust reporter (`reporters/rust/src/transformer.rs`, search for `compilation::build`) for reference implementations.

## Style Guidelines

No emojis in code or documentation. Avoid generic or boilerplate content. Be deliberate and intentional. Keep it clean and concise.

## Development

- [Development Guide](DEVELOPMENT.md) - Setup instructions and testing
- [Dev Container setup](.devcontainer/README.md) - Consistent development environment
